#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
JJ象棋 Android 训练助手 companion。
职责：
1. 通过 adb 抓取手机截图
2. 手动校准 JJ 象棋棋盘四角
3. 在默认皮肤、标准初始局面下生成识别模板
4. 识别当前局面并同步到 Web 助手

不负责任何第三方软件自动点击或代打。
"""

from __future__ import annotations

import json
import subprocess
import threading
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Callable, Dict, List, Optional, Tuple

import cv2
import numpy as np


START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w"
CANONICAL_CELL = 60
CANONICAL_BOARD_WIDTH = CANONICAL_CELL * 8
CANONICAL_BOARD_HEIGHT = CANONICAL_CELL * 9
TEMPLATE_SIZE = 52
MIN_CONFIDENCE = 0.78
STABLE_FRAME_TARGET = 2
UI_TURN_MIN_DELTA = 0.045
PROFILE_ID = "jj_xiangqi_android_default"
APP_ID = "jj_xiangqi_android"
SOURCE_NAME = "jj_android_companion"


def utc_now_iso() -> str:
    return datetime.utcnow().replace(microsecond=0).isoformat() + "Z"


def fen_to_board(fen: str) -> List[List[str]]:
    board = [["" for _ in range(9)] for _ in range(10)]
    rows = fen.split(" ")[0].split("/")
    for fen_row, row_value in enumerate(rows):
        col = 0
        for ch in row_value:
            if ch.isdigit():
                col += int(ch)
            else:
                board[fen_row][col] = ch
                col += 1
    return board


def board_to_fen(board: List[List[str]], side_to_move: str) -> str:
    rows: List[str] = []
    for row in board:
        empty = 0
        chunks: List[str] = []
        for piece in row:
            if not piece:
                empty += 1
                continue
            if empty:
                chunks.append(str(empty))
                empty = 0
            chunks.append(piece)
        if empty:
            chunks.append(str(empty))
        rows.append("".join(chunks) or "9")
    return "/".join(rows) + f" {side_to_move}"


def canonical_intersection(col: int, row_top: int) -> Tuple[int, int]:
    return col * CANONICAL_CELL, row_top * CANONICAL_CELL


def crop_patch(image: np.ndarray, center: Tuple[int, int], size: int = TEMPLATE_SIZE) -> np.ndarray:
    half = size // 2
    x, y = center
    h, w = image.shape[:2]
    left = max(0, x - half)
    top = max(0, y - half)
    right = min(w, x + half)
    bottom = min(h, y + half)
    patch = image[top:bottom, left:right]
    if patch.size == 0:
        return np.zeros((size, size, 3), dtype=np.uint8)
    if patch.shape[0] != size or patch.shape[1] != size:
        patch = cv2.resize(patch, (size, size))
    return patch


def patch_to_gray(patch: np.ndarray) -> np.ndarray:
    gray = cv2.cvtColor(patch, cv2.COLOR_BGR2GRAY)
    return cv2.GaussianBlur(gray, (3, 3), 0)


def similarity_score(a: np.ndarray, b: np.ndarray) -> float:
    return float(cv2.matchTemplate(a, b, cv2.TM_CCOEFF_NORMED)[0][0])


def detect_scrcpy_path() -> Optional[str]:
    for candidate in ("scrcpy", "scrcpy.exe"):
        try:
            subprocess.run([candidate, "--version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=3)
            return candidate
        except Exception:
            continue
    return None


def serialize_board(board: List[List[str]]) -> str:
    return "|".join("".join(cell or "." for cell in row) for row in board)


def clamp_rect(rect: Dict[str, int], frame_shape: Tuple[int, int, int]) -> Optional[Tuple[int, int, int, int]]:
    h, w = frame_shape[:2]
    left = max(0, min(w - 1, int(rect.get("left", 0))))
    top = max(0, min(h - 1, int(rect.get("top", 0))))
    right = max(left + 1, min(w, int(rect.get("right", w))))
    bottom = max(top + 1, min(h, int(rect.get("bottom", h))))
    if right - left < 8 or bottom - top < 8:
        return None
    return left, top, right, bottom


@dataclass
class RecognitionResult:
    fen: str
    side_to_move: str
    confidence: float
    board_key: str
    profile_id: str
    timestamp: str
    turn_source: str


class AndroidRecognitionCompanion:
    def __init__(
        self,
        sync_callback: Callable[[Dict], Dict],
        profile_root: Optional[Path] = None,
        adb_path: str = "adb",
        scrcpy_path: Optional[str] = None,
    ):
        self.sync_callback = sync_callback
        self.profile_root = Path(profile_root or Path(__file__).parent / "recognition_profiles")
        self.profile_root.mkdir(parents=True, exist_ok=True)
        self.profile_path = self.profile_root / f"{PROFILE_ID}.json"
        self.profile_id = PROFILE_ID
        self.adb_path = adb_path
        self.scrcpy_path = scrcpy_path or detect_scrcpy_path()
        self.lock = threading.RLock()
        self.stop_event = threading.Event()
        self.thread: Optional[threading.Thread] = None
        self.scrcpy_process: Optional[subprocess.Popen] = None

        self.executing_move = False
        self.running = False
        self.device_connected = False
        self.calibrated = False
        self.last_error = ""
        self.last_frame: Optional[np.ndarray] = None
        self.last_frame_png: Optional[bytes] = None
        self.last_frame_size: Optional[Tuple[int, int]] = None
        self.last_capture_at: Optional[str] = None
        self.last_sync_time: Optional[str] = None
        self.last_confidence: Optional[float] = None
        self.last_source = SOURCE_NAME
        self.last_fen: Optional[str] = None
        self.last_board_key: Optional[str] = None
        self.last_side_to_move = "w"
        self.sync_count = 0
        self.confidence_threshold = MIN_CONFIDENCE
        self.stable_frame_target = STABLE_FRAME_TARGET
        self.bottom_side = "red"
        self.profile: Dict = {}
        self.templates: Dict[str, List[np.ndarray]] = {}
        self.empty_templates: List[np.ndarray] = []

        self.pending_board_key: Optional[str] = None
        self.pending_board_count = 0
        self.last_candidate_fen: Optional[str] = None
        self.last_candidate_confidence: Optional[float] = None
        self.last_reject_reason: str = "等待启动"
        self.stable_frame_count = 0
        self.turn_source = "fallback_cycle"

        self._start_board_key = serialize_board(fen_to_board(START_FEN))
        self.load_profile()

    def load_profile(self):
        with self.lock:
            if not self.profile_path.exists():
                self.profile = {
                    "profile_id": self.profile_id,
                    "app_id": APP_ID,
                    "orientation": "portrait",
                    "board_bbox": None,
                    "board_corners": [],
                    "piece_template_set": [],
                    "empty_template_set": [],
                    "bottom_side": "red",
                    "river_text_style": "jj_default",
                    "turn_indicator_region": {},
                    "calibrated_at": None,
                }
                self.calibrated = False
                self.templates = {}
                self.empty_templates = []
                return

            data = json.loads(self.profile_path.read_text(encoding="utf-8"))
            self.profile = data
            self.bottom_side = data.get("bottom_side", "red")
            self.templates = {}
            for item in data.get("piece_template_set", []):
                piece = item.get("piece")
                if not piece:
                    continue
                path = self.profile_root / item["path"]
                if not path.exists():
                    continue
                img = cv2.imread(str(path))
                if img is None:
                    continue
                self.templates.setdefault(piece, []).append(patch_to_gray(img))

            self.empty_templates = []
            for path_value in data.get("empty_template_set", []):
                path = self.profile_root / path_value
                if not path.exists():
                    continue
                img = cv2.imread(str(path))
                if img is None:
                    continue
                self.empty_templates.append(patch_to_gray(img))

            self.calibrated = bool(self.profile.get("board_corners")) and bool(self.templates) and bool(self.empty_templates)
            if self.calibrated:
                self.last_reject_reason = "等待稳定局面"

    def start(self, launch_scrcpy: bool = True) -> Dict:
        with self.lock:
            if self.running:
                return self.get_status()
            self.stop_event.clear()
            self.last_error = ""
            self.last_reject_reason = "正在等待手机画面"
            self.running = True
            self.thread = threading.Thread(target=self._run_loop, daemon=True)
            self.thread.start()
            if launch_scrcpy and self.scrcpy_path:
                self._start_scrcpy()
            return self.get_status()

    def stop(self) -> Dict:
        with self.lock:
            self.stop_event.set()
            self.running = False
            self.device_connected = False
            worker = self.thread
            self.thread = None
            if self.scrcpy_process and self.scrcpy_process.poll() is None:
                try:
                    self.scrcpy_process.terminate()
                except Exception:
                    pass
            self.scrcpy_process = None
            self.last_reject_reason = "识别已停止"
        if worker and worker.is_alive():
            worker.join(timeout=1.0)
        return self.get_status()

    def recalibrate(self, points: List[List[float]], bottom_side: str = "red") -> Dict:
        with self.lock:
            if self.last_frame is None:
                raise RuntimeError("当前没有可用截图，请先启动识别")
            if len(points) != 4:
                raise ValueError("校准需要 4 个角点")

            ordered = self._normalize_points(points)
            self.bottom_side = "black" if bottom_side == "black" else "red"
            canonical = self._warp_board(self.last_frame, ordered, self.bottom_side)
            is_initial, reason = self._validate_initial_layout(canonical)
            if not is_initial:
                raise RuntimeError(reason)
            self._build_profile_from_initial_board(canonical, ordered)
            self.last_board_key = None
            self.last_fen = None
            self.last_side_to_move = "w"
            self.pending_board_key = None
            self.pending_board_count = 0
            self.last_candidate_fen = START_FEN
            self.last_candidate_confidence = 1.0
            self.last_reject_reason = "已完成校准，等待稳定局面"
            self.stable_frame_count = 0
            self.turn_source = "fallback_cycle"
            return self.get_status()

    def execute_move_on_device(self, uci_move: str) -> Dict:
        with self.lock:
            if not self.calibrated or not self.profile.get("board_corners"):
                return {"success": False, "message": "尚未校准，无法执行落子"}
            self.executing_move = True
            corners = self.profile.get("board_corners")
            bottom_side = self.profile.get("bottom_side", "red")
            
        try:
            start_col = ord(uci_move[0]) - ord('a')
            start_row = 9 - int(uci_move[1])
            end_col = ord(uci_move[2]) - ord('a')
            end_row = 9 - int(uci_move[3])

            start_canonical = canonical_intersection(start_col, start_row)
            end_canonical = canonical_intersection(end_col, end_row)

            src = np.array(corners, dtype=np.float32)
            dst = np.array([
                [0, 0],
                [CANONICAL_BOARD_WIDTH, 0],
                [CANONICAL_BOARD_WIDTH, CANONICAL_BOARD_HEIGHT],
                [0, CANONICAL_BOARD_HEIGHT],
            ], dtype=np.float32)
            matrix = cv2.getPerspectiveTransform(src, dst)
            inv_matrix = np.linalg.inv(matrix)

            def get_screen_pt(canonical_pt):
                cx, cy = canonical_pt
                if bottom_side == "black":
                    cx = CANONICAL_BOARD_WIDTH - cx
                    cy = CANONICAL_BOARD_HEIGHT - cy
                
                pt = np.array([[[cx, cy]]], dtype=np.float32)
                transformed = cv2.perspectiveTransform(pt, inv_matrix)
                return int(transformed[0][0][0]), int(transformed[0][0][1])

            start_x, start_y = get_screen_pt(start_canonical)
            end_x, end_y = get_screen_pt(end_canonical)

            cmd = f'input tap {start_x} {start_y} && sleep 0.1 && input tap {end_x} {end_y}'
            subprocess.run([self.adb_path, "shell", cmd], check=True)
            
            time.sleep(1.2)
            
            return {"success": True, "message": f"落子 {uci_move} 已执行"}
        except Exception as e:
            return {"success": False, "message": f"执行落子失败: {e}"}
        finally:
            with self.lock:
                self.executing_move = False

    def get_status(self) -> Dict:
        with self.lock:
            return {
                "running": self.running,
                "device_connected": self.device_connected,
                "scrcpy_running": bool(self.scrcpy_process and self.scrcpy_process.poll() is None),
                "calibrated": self.calibrated,
                "profile_id": self.profile.get("profile_id", self.profile_id),
                "confidence_threshold": self.confidence_threshold,
                "last_error": self.last_error,
                "last_capture_at": self.last_capture_at,
                "last_sync_time": self.last_sync_time,
                "last_confidence": self.last_confidence,
                "last_source": self.last_source,
                "last_fen": self.last_fen,
                "last_candidate_fen": self.last_candidate_fen,
                "last_candidate_confidence": self.last_candidate_confidence,
                "last_reject_reason": self.last_reject_reason,
                "stable_frame_count": self.stable_frame_count,
                "turn_source": self.turn_source,
                "bottom_side": self.bottom_side,
                "frame_width": self.last_frame_size[0] if self.last_frame_size else None,
                "frame_height": self.last_frame_size[1] if self.last_frame_size else None,
                "sync_count": self.sync_count,
                "stable_frame_target": self.stable_frame_target,
                "app_id": self.profile.get("app_id", APP_ID),
                "turn_indicator_region": self.profile.get("turn_indicator_region", {}),
            }

    def get_latest_frame_bytes(self) -> Optional[bytes]:
        with self.lock:
            return self.last_frame_png

    def _start_scrcpy(self):
        if not self.scrcpy_path or (self.scrcpy_process and self.scrcpy_process.poll() is None):
            return
        try:
            self.scrcpy_process = subprocess.Popen(
                [self.scrcpy_path, "--window-title", "Chinese Chess Companion"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        except Exception:
            self.scrcpy_process = None

    def _adb_prefix(self) -> List[str]:
        return [self.adb_path]

    def _capture_frame(self) -> np.ndarray:
        cmd = self._adb_prefix() + ["exec-out", "screencap", "-p"]
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=8, check=True)
        data = result.stdout.replace(b"\r\n", b"\n")
        img = cv2.imdecode(np.frombuffer(data, dtype=np.uint8), cv2.IMREAD_COLOR)
        if img is None:
            raise RuntimeError("无法解析 adb 截图")
        return img

    def _run_loop(self):
        while not self.stop_event.is_set():
            try:
                with self.lock:
                    is_executing = self.executing_move
                if is_executing:
                    time.sleep(0.5)
                    continue

                frame = self._capture_frame()
                with self.lock:
                    self.device_connected = True
                    self.last_frame = frame
                    self.last_frame_size = (int(frame.shape[1]), int(frame.shape[0]))
                    ok, encoded = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), 82])
                    self.last_frame_png = encoded.tobytes() if ok else None
                    self.last_capture_at = utc_now_iso()
                    self.last_error = ""
                    if not self.calibrated:
                        self.last_reject_reason = "等待 JJ 象棋默认皮肤初始局面校准"

                if self.calibrated:
                    result = self._recognize_current_position(frame)
                    if result:
                        payload = {
                            "fen": result.fen,
                            "candidate_fen": result.fen,
                            "source": self.last_source,
                            "confidence": result.confidence,
                            "candidate_confidence": result.confidence,
                            "profile_id": result.profile_id,
                            "timestamp": result.timestamp,
                            "stable_frame_count": self.stable_frame_count,
                            "turn_source": result.turn_source,
                            "reject_reason": "",
                        }
                        sync_response = self.sync_callback(payload)
                        accepted = bool(sync_response.get("accepted"))
                        with self.lock:
                            if accepted:
                                self.last_fen = result.fen
                                self.last_board_key = result.board_key
                                self.last_side_to_move = result.side_to_move
                                self.last_confidence = result.confidence
                                self.last_sync_time = result.timestamp
                                self.last_candidate_fen = result.fen
                                self.last_candidate_confidence = result.confidence
                                self.last_reject_reason = ""
                                self.turn_source = result.turn_source
                                self.sync_count += 1
                            else:
                                self.last_reject_reason = sync_response.get("message") or self.last_reject_reason
                time.sleep(1.0)
            except subprocess.CalledProcessError as exc:
                with self.lock:
                    self.device_connected = False
                    self.last_error = (exc.stderr or b"").decode("utf-8", errors="ignore").strip() or "adb 截图失败"
                    self.last_reject_reason = "未获取到 Android 设备截图"
                time.sleep(1.5)
            except Exception as exc:
                with self.lock:
                    self.device_connected = False
                    self.last_error = str(exc)
                    self.last_reject_reason = str(exc)
                time.sleep(1.5)

    def _normalize_points(self, points: List[List[float]]) -> List[List[float]]:
        pts = np.array(points, dtype=np.float32)
        s = pts.sum(axis=1)
        diff = np.diff(pts, axis=1).reshape(-1)
        top_left = pts[np.argmin(s)]
        bottom_right = pts[np.argmax(s)]
        top_right = pts[np.argmin(diff)]
        bottom_left = pts[np.argmax(diff)]
        return [
            [float(top_left[0]), float(top_left[1])],
            [float(top_right[0]), float(top_right[1])],
            [float(bottom_right[0]), float(bottom_right[1])],
            [float(bottom_left[0]), float(bottom_left[1])],
        ]

    def _warp_board(self, frame: np.ndarray, corners: List[List[float]], bottom_side: str) -> np.ndarray:
        src = np.array(corners, dtype=np.float32)
        dst = np.array(
            [
                [0, 0],
                [CANONICAL_BOARD_WIDTH, 0],
                [CANONICAL_BOARD_WIDTH, CANONICAL_BOARD_HEIGHT],
                [0, CANONICAL_BOARD_HEIGHT],
            ],
            dtype=np.float32,
        )
        matrix = cv2.getPerspectiveTransform(src, dst)
        warped = cv2.warpPerspective(frame, matrix, (CANONICAL_BOARD_WIDTH, CANONICAL_BOARD_HEIGHT))
        if bottom_side == "black":
            warped = cv2.rotate(warped, cv2.ROTATE_180)
        return warped

    def _build_profile_from_initial_board(self, canonical: np.ndarray, corners: List[List[float]]):
        board = fen_to_board(START_FEN)
        template_dir = self.profile_root / self.profile_id
        template_dir.mkdir(parents=True, exist_ok=True)

        piece_template_set: List[Dict[str, str]] = []
        piece_counter: Dict[str, int] = {}
        self.templates = {}
        for row in range(10):
            for col in range(9):
                piece = board[row][col]
                if not piece:
                    continue
                cx, cy = canonical_intersection(col, row)
                patch = crop_patch(canonical, (cx, cy))
                index = piece_counter.get(piece, 0)
                filename = f"{piece}_{index}.png"
                cv2.imwrite(str(template_dir / filename), patch)
                self.templates.setdefault(piece, []).append(patch_to_gray(patch))
                piece_template_set.append({"piece": piece, "path": f"{self.profile_id}/{filename}"})
                piece_counter[piece] = index + 1

        empty_positions = [
            (4, 4), (0, 4), (8, 4), (2, 4), (6, 4), (4, 1), (4, 8), (1, 5), (7, 5), (4, 5)
        ]
        self.empty_templates = []
        empty_template_set: List[str] = []
        for idx, (col, row) in enumerate(empty_positions):
            cx, cy = canonical_intersection(col, row)
            patch = crop_patch(canonical, (cx, cy))
            filename = f"empty_{idx}.png"
            cv2.imwrite(str(template_dir / filename), patch)
            self.empty_templates.append(patch_to_gray(patch))
            empty_template_set.append(f"{self.profile_id}/{filename}")

        x_values = [int(point[0]) for point in corners]
        y_values = [int(point[1]) for point in corners]
        frame_size = self.last_frame_size or (0, 0)
        self.profile = {
            "profile_id": self.profile_id,
            "app_id": APP_ID,
            "orientation": "portrait",
            "board_bbox": {
                "left": min(x_values),
                "top": min(y_values),
                "right": max(x_values),
                "bottom": max(y_values),
            },
            "board_corners": corners,
            "piece_template_set": piece_template_set,
            "empty_template_set": empty_template_set,
            "bottom_side": self.bottom_side,
            "river_text_style": "jj_default",
            "turn_indicator_region": self._estimate_turn_indicator_region(corners, frame_size),
            "calibrated_at": utc_now_iso(),
        }
        self.profile_path.write_text(json.dumps(self.profile, ensure_ascii=False, indent=2), encoding="utf-8")
        self.calibrated = True

    def _validate_initial_layout(self, canonical: np.ndarray) -> Tuple[bool, str]:
        start_board = fen_to_board(START_FEN)
        occupied_positions: List[Tuple[int, int]] = []
        empty_positions: List[Tuple[int, int]] = []
        for row in range(10):
            for col in range(9):
                if start_board[row][col]:
                    occupied_positions.append((col, row))
                else:
                    empty_positions.append((col, row))

        empty_samples = [
            patch_to_gray(crop_patch(canonical, canonical_intersection(col, row)))
            for col, row in empty_positions
        ]
        if not empty_samples:
            return False, "未能生成 JJ 象棋空位模板，请重新校准"

        empty_reference = np.mean(np.stack(empty_samples, axis=0).astype(np.float32), axis=0)

        def occupancy_score(col: int, row: int) -> float:
            patch = patch_to_gray(crop_patch(canonical, canonical_intersection(col, row))).astype(np.float32)
            diff_score = float(np.mean(np.abs(patch - empty_reference)) / 255.0)
            edge_score = float(np.mean(cv2.Canny(patch.astype(np.uint8), 60, 120) > 0))
            center = patch[patch.shape[0] // 4: patch.shape[0] * 3 // 4, patch.shape[1] // 4: patch.shape[1] * 3 // 4]
            center_var = float(np.std(center) / 255.0)
            return (diff_score * 0.6) + (edge_score * 0.25) + (center_var * 0.15)

        occupied_scores = [occupancy_score(col, row) for col, row in occupied_positions]
        empty_scores = [occupancy_score(col, row) for col, row in empty_positions]
        occupied_median = float(np.median(occupied_scores)) if occupied_scores else 0.0
        empty_median = float(np.median(empty_scores)) if empty_scores else 0.0
        threshold = (occupied_median + empty_median) / 2.0

        if occupied_median - empty_median < 0.06:
            return False, "当前画面不像 JJ 象棋默认皮肤初始局面，请回到开局后重新校准"

        missing = [(col, row) for col, row in occupied_positions if occupancy_score(col, row) <= threshold]
        unexpected = [(col, row) for col, row in empty_positions if occupancy_score(col, row) >= threshold]

        if missing or unexpected:
            return False, "校准失败：当前并非 JJ 象棋默认皮肤开局局面，或四角点偏差过大"

        return True, ""

    def _estimate_turn_indicator_region(self, corners: List[List[float]], frame_size: Tuple[int, int]) -> Dict[str, Dict[str, int]]:
        frame_w, frame_h = frame_size if frame_size else (0, 0)
        x_values = [float(point[0]) for point in corners]
        y_values = [float(point[1]) for point in corners]
        left = min(x_values)
        right = max(x_values)
        top = min(y_values)
        bottom = max(y_values)
        board_width = right - left
        board_height = bottom - top
        if frame_w <= 0 or frame_h <= 0:
            return {}

        region_width = int(min(frame_w - 20, board_width * 1.25))
        region_height = int(max(48, min(frame_h * 0.12, board_height * 0.18)))
        center_x = int((left + right) / 2)
        half_width = region_width // 2

        top_center_y = int(max(region_height // 2 + 6, top - board_height * 0.22))
        bottom_center_y = int(min(frame_h - region_height // 2 - 6, bottom + board_height * 0.22))

        def make_rect(center_y: int) -> Dict[str, int]:
            return {
                "left": max(0, center_x - half_width),
                "top": max(0, center_y - region_height // 2),
                "right": min(frame_w, center_x + half_width),
                "bottom": min(frame_h, center_y + region_height // 2),
            }

        return {
            "top": make_rect(top_center_y),
            "bottom": make_rect(bottom_center_y),
        }

    def _recognize_current_position(self, frame: np.ndarray) -> Optional[RecognitionResult]:
        with self.lock:
            corners = self.profile.get("board_corners") or []
            bottom_side = self.profile.get("bottom_side", "red")
            if not corners or not self.templates or not self.empty_templates:
                self.last_reject_reason = "尚未完成 JJ 象棋专用校准"
                return None

        canonical = self._warp_board(frame, corners, bottom_side)
        recognized_board, confidence = self._classify_board(canonical)
        board_key = serialize_board(recognized_board)
        side_to_move, turn_source = self._resolve_side_to_move(frame, board_key)
        fen = board_to_fen(recognized_board, side_to_move)

        with self.lock:
            self.last_candidate_fen = fen
            self.last_candidate_confidence = confidence
            self.turn_source = turn_source

        if confidence < self.confidence_threshold:
            self._set_reject(f"识别置信度偏低（{confidence:.2f}），请确认 JJ 默认皮肤、无遮挡并保持画面稳定", confidence, fen, 0, turn_source)
            return None

        if not self._is_board_shape_valid(recognized_board):
            self._set_reject("棋子分布不合法，请确认是 JJ 象棋默认皮肤，并从标准初始局面完成校准", confidence, fen, 0, turn_source)
            return None

        if not self._is_single_move_progression(recognized_board):
            self._set_reject("检测到跨步跳变，疑似漏识别多手或抓到了动画中间帧，请等待稳定后重试", confidence, fen, 0, turn_source)
            return None

        if board_key == self.last_board_key:
            self._set_reject("局面未变化，等待下一手", confidence, fen, self.stable_frame_target, turn_source)
            return None

        with self.lock:
            if board_key == self.pending_board_key:
                self.pending_board_count += 1
            else:
                self.pending_board_key = board_key
                self.pending_board_count = 1
            self.stable_frame_count = self.pending_board_count

        if self.pending_board_count < self.stable_frame_target:
            self._set_reject(
                f"等待稳定帧 {self.pending_board_count}/{self.stable_frame_target}，动画或拖动过程中不会同步",
                confidence,
                fen,
                self.pending_board_count,
                turn_source,
            )
            return None

        return RecognitionResult(
            fen=fen,
            side_to_move=side_to_move,
            confidence=confidence,
            board_key=board_key,
            profile_id=self.profile_id,
            timestamp=utc_now_iso(),
            turn_source=turn_source,
        )

    def _set_reject(
        self,
        reason: str,
        confidence: Optional[float],
        candidate_fen: Optional[str],
        stable_count: int,
        turn_source: str,
    ):
        with self.lock:
            self.last_reject_reason = reason
            self.last_candidate_confidence = confidence
            if candidate_fen:
                self.last_candidate_fen = candidate_fen
            self.stable_frame_count = stable_count
            self.turn_source = turn_source

    def _resolve_side_to_move(self, frame: np.ndarray, board_key: str) -> Tuple[str, str]:
        ui_side = self._detect_side_to_move_from_ui(frame)
        if ui_side:
            return ui_side, "jj_ui_indicator"
        return self._infer_side_to_move(board_key), "fallback_cycle"

    def _detect_side_to_move_from_ui(self, frame: np.ndarray) -> Optional[str]:
        with self.lock:
            regions = self.profile.get("turn_indicator_region") or {}
            bottom_side = self.bottom_side
        top_rect = clamp_rect(regions.get("top", {}), frame.shape) if regions else None
        bottom_rect = clamp_rect(regions.get("bottom", {}), frame.shape) if regions else None
        if not top_rect or not bottom_rect:
            return None

        top_score = self._turn_activity_score(frame, top_rect)
        bottom_score = self._turn_activity_score(frame, bottom_rect)
        delta = bottom_score - top_score
        if abs(delta) < UI_TURN_MIN_DELTA:
            return None

        active_screen_side = "bottom" if delta > 0 else "top"
        if active_screen_side == "bottom":
            active_side = bottom_side
        else:
            active_side = "black" if bottom_side == "red" else "red"
        return "w" if active_side == "red" else "b"

    def _turn_activity_score(self, frame: np.ndarray, rect: Tuple[int, int, int, int]) -> float:
        left, top, right, bottom = rect
        region = frame[top:bottom, left:right]
        if region.size == 0:
            return 0.0
        hsv = cv2.cvtColor(region, cv2.COLOR_BGR2HSV)
        gray = cv2.cvtColor(region, cv2.COLOR_BGR2GRAY)
        edges = cv2.Canny(gray, 60, 120)
        sat = float(np.mean(hsv[..., 1]) / 255.0)
        val = float(np.mean(hsv[..., 2]) / 255.0)
        colorful_ratio = float(np.mean((hsv[..., 1] > 70) & (hsv[..., 2] > 110)))
        bright_ratio = float(np.mean(hsv[..., 2] > 150))
        edge_ratio = float(np.mean(edges > 0))
        return (colorful_ratio * 0.4) + (edge_ratio * 0.25) + (bright_ratio * 0.2) + (sat * 0.1) + (val * 0.05)

    def _classify_board(self, canonical: np.ndarray) -> Tuple[List[List[str]], float]:
        board: List[List[str]] = [["" for _ in range(9)] for _ in range(10)]
        confidence_scores: List[float] = []

        for row in range(10):
            for col in range(9):
                cx, cy = canonical_intersection(col, row)
                gray_patch = patch_to_gray(crop_patch(canonical, (cx, cy)))

                best_label = ""
                best_score = -1.0
                second_score = -1.0

                for template in self.empty_templates:
                    score = similarity_score(gray_patch, template)
                    if score > best_score:
                        second_score = best_score
                        best_score = score
                        best_label = ""
                    elif score > second_score:
                        second_score = score

                for piece, template_list in self.templates.items():
                    for template in template_list:
                        score = similarity_score(gray_patch, template)
                        if score > best_score:
                            second_score = best_score
                            best_score = score
                            best_label = piece
                        elif score > second_score:
                            second_score = score

                board[row][col] = best_label
                gap_bonus = max(0.0, best_score - max(second_score, 0.0))
                confidence_scores.append(min(1.0, (best_score * 0.8) + (gap_bonus * 0.2)))

        return board, float(np.mean(confidence_scores)) if confidence_scores else 0.0

    def _infer_side_to_move(self, board_key: str) -> str:
        if board_key == self._start_board_key:
            self.last_side_to_move = "w"
            return "w"
        self.last_side_to_move = "b" if self.last_side_to_move == "w" else "w"
        return self.last_side_to_move

    def _is_board_shape_valid(self, board: List[List[str]]) -> bool:
        pieces = [piece for row in board for piece in row if piece]
        if sum(piece == "k" for piece in pieces) != 1 or sum(piece == "K" for piece in pieces) != 1:
            return False
        allowed_counts = {
            "K": 1, "k": 1, "A": 2, "a": 2, "B": 2, "b": 2,
            "N": 2, "n": 2, "R": 2, "r": 2, "C": 2, "c": 2,
            "P": 5, "p": 5,
        }
        for piece, max_count in allowed_counts.items():
            if sum(item == piece for item in pieces) > max_count:
                return False
        return True

    def _is_single_move_progression(self, board: List[List[str]]) -> bool:
        reference_fen = self.last_fen or START_FEN
        reference_board = fen_to_board(reference_fen)
        if serialize_board(reference_board) == serialize_board(board):
            return True
        diff_count = 0
        for row in range(10):
            for col in range(9):
                if reference_board[row][col] != board[row][col]:
                    diff_count += 1

        if self.last_fen is None:
            return diff_count in (0, 2)
        return diff_count == 2


__all__ = ["AndroidRecognitionCompanion"]
