#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
涓浗璞℃ AI 鍔╂墜 - Web 鍥惧舰鐣岄潰鐗堟湰
鍩轰簬 Flask 鎻愪緵 Web 鐣岄潰
"""

from flask import Flask, render_template, jsonify, request, Response, make_response
import subprocess
import threading
import queue
import time
import os
import argparse
import atexit
import re
from pathlib import Path

try:
    from recognition_companion import AndroidRecognitionCompanion
    RECOGNITION_IMPORT_ERROR = None
except Exception as exc:
    AndroidRecognitionCompanion = None
    RECOGNITION_IMPORT_ERROR = str(exc)

try:
    from move_translator import uci_to_chinese
except Exception:
    def uci_to_chinese(move, fen=None):
        return move

app = Flask(__name__)

DEFAULT_ANALYZE_DEPTH = 18
DEFAULT_HASH_MB = 128
DEFAULT_THREADS = min(max((os.cpu_count() or 1) - 1, 1), 4)


def is_truthy(value: str) -> bool:
    """."""
    """."""
    return str(value).strip().lower() in {"1", "true", "yes", "on"}


def parse_runtime_args():
    """."""
    """."""
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--hot-reload", action="store_true", dest="hot_reload")
    parser.add_argument("--host", default=os.getenv("CHESS_WEB_HOST", "0.0.0.0"))
    parser.add_argument("--port", type=int, default=int(os.getenv("CHESS_WEB_PORT", "5000")))
    args, _ = parser.parse_known_args()

    env_hot = is_truthy(os.getenv("CHESS_HOT_RELOAD", "0"))
    args.hot_reload = args.hot_reload or env_hot
    return args


def collect_hot_reload_files():
    """."""
    """."""
    root = Path(__file__).parent
    templates = root / "templates"
    files = []
    if templates.exists():
        files.extend(str(p) for p in templates.rglob("*.html"))
    return files


def parse_depth(value, default=DEFAULT_ANALYZE_DEPTH):
    try:
        depth = int(value)
    except (TypeError, ValueError):
        return default
    return max(5, min(depth, 30))


def build_engine_payload(extra=None):
    payload = engine.get_state()
    if extra:
        payload.update(extra)
    return payload


def is_valid_xiangqi_fen(fen: str) -> bool:
    try:
        parts = str(fen or "").strip().split(" ")
        if len(parts) < 2:
            return False
        rows = parts[0].split("/")
        if len(rows) != 10:
            return False
        for row in rows:
            total = 0
            for ch in row:
                if ch.isdigit():
                    total += int(ch)
                elif ch in "rnbakcpRNBAKCP":
                    total += 1
                else:
                    return False
            if total != 9:
                return False
        return parts[1] in {"w", "b"}
    except Exception:
        return False

class ChessEngine:
    """."""
    """."""
    
    def __init__(self):
        self.engine_path = Path(__file__).parent / "src" / "pikafish.exe"
        self.engine = None
        self.is_ready = False
        self.output_queue = queue.Queue()
        self.current_position = []  # 存储当前局面的着法序列
        self.base_fen = None
        self.engine_lock = threading.RLock()
        self.threads = DEFAULT_THREADS
        self.hash_mb = DEFAULT_HASH_MB
        self._cached_legal_moves = []  # 合法着法缓存，局面变化时自动刷新
        self._abort_event = threading.Event()  # 用于无锁中断分析
        self._analysis_active = False
        
    def start(self):
        """."""
        """."""
        with self.engine_lock:
            try:
                if self.engine and self.engine.poll() is None and self.is_ready:
                    return True

                if self.engine and self.engine.poll() is not None:
                    self.engine = None
                    self.is_ready = False

                self.engine = subprocess.Popen(
                    [str(self.engine_path.absolute())],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    universal_newlines=True,  # Python 3.6 兼容
                    bufsize=1,
                    cwd=str(self.engine_path.parent.absolute())
                )
                
                threading.Thread(target=self._read_output, daemon=True).start()
                self._drain_output_queue()
                self._send("uci")
                if not self._wait_for_prefix("uciok", timeout=5):
                    raise RuntimeError("寮曟搸鍒濆鍖栧け璐ワ細鏈敹鍒?uciok")
                self._configure_engine_options()
                self._apply_position_unlocked()
                self._wait_ready()
                self.is_ready = True
                return True
            except Exception as e:
                self.is_ready = False
                try:
                    self.stop()
                except Exception:
                    pass
                print(f"鍚姩寮曟搸澶辫触: {e}")
                return False
    
    def _send(self, command):
        """."""
        """."""
        if self.engine and self.engine.stdin:
            self.engine.stdin.write(command + "\n")
            self.engine.stdin.flush()

    def _drain_output_queue(self):
        while True:
            try:
                self.output_queue.get_nowait()
            except queue.Empty:
                break

    def _wait_for_prefix(self, prefix, timeout=5):
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                line = self.output_queue.get(timeout=0.1)
            except queue.Empty:
                continue
            if line.startswith(prefix):
                return line
        raise TimeoutError(f"绛夊緟寮曟搸杈撳嚭瓒呮椂: {prefix}")

    def _wait_ready(self, timeout=5):
        self._drain_output_queue()
        self._send("isready")
        self._wait_for_prefix("readyok", timeout=timeout)

    def _configure_engine_options(self):
        self._send(f"setoption name Threads value {self.threads}")
        self._send(f"setoption name Hash value {self.hash_mb}")
        self._send("setoption name MultiPV value 1")
        self._wait_ready()

    def _apply_position_unlocked(self):
        """设置引擎局面并刷新合法着法缓存。必须在持有 engine_lock 时调用。"""
        if self.base_fen:
            cmd = f"position fen {self.base_fen}"
        else:
            cmd = "position startpos"
        if self.current_position:
            cmd += " moves " + " ".join(self.current_position)
        self._send(cmd)
        self._refresh_legal_moves_cache()

    def _refresh_legal_moves_cache(self):
        """通过 perft 1 刷新合法着法缓存。必须在持有 engine_lock 时调用。"""
        self._drain_output_queue()
        self._send("go perft 1")
        legal_moves = []
        timeout = time.time() + 10
        while time.time() < timeout:
            try:
                line = self.output_queue.get(timeout=0.1)
            except queue.Empty:
                continue
            match = re.match(r"^([a-i][0-9][a-i][0-9][a-z]?)\s*:\s*\d+", line)
            if match:
                legal_moves.append(match.group(1))
                continue
            if line.startswith("Nodes searched:"):
                break
        self._cached_legal_moves = legal_moves
    
    def _read_output(self):
        """."""
        """."""
        while self.engine and self.engine.poll() is None:
            try:
                line = self.engine.stdout.readline()
                if line:
                    self.output_queue.put(line.strip())
            except:
                break
    
    def set_position(self, moves=None):
        """设置局面着法序列。"""
        with self.engine_lock:
            self._stop_analysis_unlocked()
            self.current_position = list(moves or [])
            if self.is_ready:
                self._apply_position_unlocked()

    def add_move(self, move):
        """添加一步着法。"""
        with self.engine_lock:
            self.current_position.append(move)
            if self.is_ready:
                self._apply_position_unlocked()
    
    def stop_analysis(self):
        """中断正在进行的分析。不需要锁，可从任何线程安全调用。
        
        工作原理：
        1. 设置 _abort_event 标志
        2. 直接向引擎 stdin 发送 'stop'
        3. 正在运行的 analyze() 检测到标志后会立即退出并释放锁
        """
        if not self._analysis_active:
            return
        self._abort_event.set()
        try:
            if self.engine and self.engine.stdin and self.engine.poll() is None:
                self.engine.stdin.write("stop\n")
                self.engine.stdin.flush()
        except Exception:
            pass

    def _stop_analysis_unlocked(self):
        """内部方法：发送 stop 命令并排空输出。必须在持有 engine_lock 时调用。"""
        if self.engine and self.engine.poll() is None and self._analysis_active:
            self._send("stop")
            # 等待引擎输出 bestmove（stop 后引擎会立即回复 bestmove）
            deadline = time.time() + 2
            while time.time() < deadline:
                try:
                    line = self.output_queue.get(timeout=0.05)
                    if line.startswith("bestmove"):
                        break
                except queue.Empty:
                    continue
            self._analysis_active = False
            self._drain_output_queue()

    def analyze(self, depth=DEFAULT_ANALYZE_DEPTH, multipv=1):
        """分析当前局面。可通过 stop_analysis() 从外部中断。"""
        self._abort_event.clear()
        with self.engine_lock:
            if not self.is_ready:
                return {'best_move': None, 'score': None, 'depth': 0, 'pv': []}

            self._stop_analysis_unlocked()
            self._drain_output_queue()
            if multipv > 1:
                self._send(f"setoption name MultiPV value {multipv}")
                self._wait_ready()

            self._send(f"go depth {depth}")
            self._analysis_active = True
            
            best_move = None
            score = None
            pv = []
            depth_reached = 0
            multipv_results = {}
            
            timeout = time.time() + 30
            while time.time() < timeout:
                # 检查是否被外部 stop_analysis() 中断
                if self._abort_event.is_set():
                    # 被中断：等待引擎回复 bestmove 后退出
                    abort_deadline = time.time() + 2
                    while time.time() < abort_deadline:
                        try:
                            line = self.output_queue.get(timeout=0.05)
                            if line.startswith("bestmove"):
                                parts = line.split()
                                if len(parts) > 1:
                                    best_move = best_move or parts[1]
                                break
                        except queue.Empty:
                            continue
                    self._drain_output_queue()
                    break

                try:
                    line = self.output_queue.get(timeout=0.1)
                    
                    if line.startswith("info"):
                        current_pv_num = 1
                        if "multipv" in line:
                            parts = line.split()
                            if "multipv" in parts:
                                idx = parts.index("multipv")
                                if idx + 1 < len(parts):
                                    current_pv_num = int(parts[idx + 1])
                        
                        if current_pv_num not in multipv_results:
                            multipv_results[current_pv_num] = {'move': None, 'score': None, 'pv': []}
                        
                        if "depth" in line:
                            parts = line.split()
                            if "depth" in parts:
                                idx = parts.index("depth")
                                if idx + 1 < len(parts):
                                    depth_reached = max(depth_reached, int(parts[idx + 1]))
                        
                        current_score = None
                        if "score cp" in line:
                            parts = line.split()
                            if "cp" in parts:
                                idx = parts.index("cp")
                                if idx + 1 < len(parts):
                                    current_score = int(parts[idx + 1])
                        
                        if "score mate" in line:
                            parts = line.split()
                            if "mate" in parts:
                                idx = parts.index("mate")
                                if idx + 1 < len(parts):
                                    mate_in = int(parts[idx + 1])
                                    current_score = f"杀棋{mate_in}"
                        
                        if current_score is not None:
                            multipv_results[current_pv_num]['score'] = current_score
                            if current_pv_num == 1:
                                score = current_score
                        
                        if "pv" in line:
                            parts = line.split()
                            if "pv" in parts:
                                idx = parts.index("pv")
                                current_pv = parts[idx + 1:idx + 6]
                                multipv_results[current_pv_num]['pv'] = current_pv
                                if current_pv_num == 1:
                                    pv = current_pv
                                if current_pv:
                                    multipv_results[current_pv_num]['move'] = current_pv[0]
                    
                    elif line.startswith("bestmove"):
                        parts = line.split()
                        if len(parts) > 1:
                            best_move = parts[1]
                        self._analysis_active = False
                        break
                        
                except queue.Empty:
                    continue

            self._analysis_active = False
            
            if multipv > 1:
                self._send("setoption name MultiPV value 1")
                self._wait_ready()
            
            if multipv > 1:
                moves_list = []
                for i in range(1, multipv + 1):
                    if i in multipv_results and multipv_results[i]['move']:
                        moves_list.append({
                            'move': multipv_results[i]['move'],
                            'score': multipv_results[i]['score'],
                            'pv': multipv_results[i]['pv']
                        })
                return {
                    'moves': moves_list,
                    'depth': depth_reached
                }
            return {
                'best_move': best_move,
                'score': score,
                'depth': depth_reached,
                'pv': pv
            }

    def get_legal_moves(self):
        """返回当前局面的合法着法列表（从缓存中读取，不阻塞引擎）。"""
        return list(self._cached_legal_moves)

    def get_state(self):
        """返回引擎当前状态（读取缓存，不触发引擎操作）。"""
        with self.engine_lock:
            return {
                'is_ready': self.is_ready,
                'moves': list(self.current_position),
                'base_fen': self.base_fen,
                'legal_moves': list(self._cached_legal_moves)
            }
    
    def reset(self):
        """重置棋局到初始局面。"""
        self.stop_analysis()
        with self.engine_lock:
            self._stop_analysis_unlocked()
            self.base_fen = None
            self.current_position = []
            if self.is_ready:
                self._apply_position_unlocked()

    def set_fen(self, fen: str):
        """根据 FEN 设置局面。"""
        with self.engine_lock:
            self._stop_analysis_unlocked()
            self.base_fen = fen
            self.current_position = []
            if self.is_ready:
                self._apply_position_unlocked()

    def stop(self):
        """关闭引擎进程。"""
        with self.engine_lock:
            if self.engine:
                try:
                    self._send("quit")
                    self.engine.wait(timeout=5)
                except Exception:
                    pass
                self.engine = None
                self.is_ready = False
                self._cached_legal_moves = []
                self._drain_output_queue()

# 鍏ㄥ眬寮曟搸瀹炰緥
engine = ChessEngine()
recognition_lock = threading.RLock()
RECOGNITION_SYNC_MIN_CONFIDENCE = 0.78
RECOGNITION_SYNC_MIN_STABLE_FRAMES = 2
recognition_runtime = {
    'connected': False,
    'last_sync_time': None,
    'last_confidence': None,
    'last_source': None,
    'last_profile_id': None,
    'last_fen': None,
    'accepted_count': 0,
    'last_candidate_fen': None,
    'last_candidate_confidence': None,
    'last_reject_reason': '等待识别启动',
    'stable_frame_count': 0,
    'turn_source': 'fallback_cycle',
}


def apply_position_sync_payload(payload):
    payload = payload or {}
    fen = str(payload.get('fen', '')).strip()
    source = str(payload.get('source', 'android_companion')).strip() or 'android_companion'
    profile_id = str(payload.get('profile_id', '')).strip() or None
    timestamp = str(payload.get('timestamp', '')).strip() or time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
    candidate_fen = str(payload.get('candidate_fen', fen)).strip() or fen
    reject_reason = str(payload.get('reject_reason', '')).strip()
    turn_source = str(payload.get('turn_source', 'fallback_cycle')).strip() or 'fallback_cycle'
    try:
        confidence = float(payload.get('confidence', 0))
    except (TypeError, ValueError):
        confidence = 0.0
    try:
        candidate_confidence = float(payload.get('candidate_confidence', confidence))
    except (TypeError, ValueError):
        candidate_confidence = confidence
    try:
        stable_frame_count = int(payload.get('stable_frame_count', 0))
    except (TypeError, ValueError):
        stable_frame_count = 0

    with recognition_lock:
        recognition_runtime.update({
            'connected': True,
            'last_candidate_fen': candidate_fen or fen or None,
            'last_candidate_confidence': candidate_confidence,
            'last_reject_reason': reject_reason or recognition_runtime.get('last_reject_reason') or '',
            'stable_frame_count': stable_frame_count,
            'turn_source': turn_source,
        })

    if not is_valid_xiangqi_fen(fen):
        return build_engine_payload({
            'success': False,
            'accepted': False,
            'message': '识别局面格式无效'
        })

    if confidence < RECOGNITION_SYNC_MIN_CONFIDENCE:
        return build_engine_payload({
            'success': True,
            'accepted': False,
            'message': '识别置信度过低，已忽略本次同步',
            'confidence': confidence,
        })

    if stable_frame_count < RECOGNITION_SYNC_MIN_STABLE_FRAMES:
        return build_engine_payload({
            'success': True,
            'accepted': False,
            'message': f'稳定帧不足（{stable_frame_count}/{RECOGNITION_SYNC_MIN_STABLE_FRAMES}），等待更稳定画面',
            'confidence': confidence,
            'stable_frame_count': stable_frame_count,
        })

    with recognition_lock:
        with engine.engine_lock:
            current_engine_fen = engine.base_fen
        if recognition_runtime.get('last_fen') == fen and current_engine_fen == fen:
            return build_engine_payload({
                'success': True,
                'accepted': False,
                'message': '局面未变化，跳过重复同步',
                'confidence': confidence,
            })

        engine.set_fen(fen)
        recognition_runtime.update({
            'connected': True,
            'last_sync_time': timestamp,
            'last_confidence': confidence,
            'last_source': source,
            'last_profile_id': profile_id,
            'last_fen': fen,
            'last_candidate_fen': candidate_fen or fen,
            'last_candidate_confidence': candidate_confidence,
            'last_reject_reason': '',
            'stable_frame_count': stable_frame_count,
            'turn_source': turn_source,
            'accepted_count': int(recognition_runtime.get('accepted_count', 0)) + 1,
        })

    return build_engine_payload({
        'success': True,
        'accepted': True,
        'message': '已同步识别局面',
        'confidence': confidence,
        'source': source,
        'profile_id': profile_id,
        'synced_at': timestamp,
        'stable_frame_count': stable_frame_count,
        'turn_source': turn_source,
    })


recognition_companion = AndroidRecognitionCompanion(sync_callback=apply_position_sync_payload) if AndroidRecognitionCompanion else None


def build_recognition_payload(extra=None):
    if recognition_companion:
        payload = recognition_companion.get_status()
    else:
        payload = {
            'available': False,
            'running': False,
            'device_connected': False,
            'scrcpy_running': False,
            'calibrated': False,
            'profile_id': None,
            'confidence_threshold': RECOGNITION_SYNC_MIN_CONFIDENCE,
            'last_error': f'识别依赖不可用: {RECOGNITION_IMPORT_ERROR}' if RECOGNITION_IMPORT_ERROR else '识别助手不可用',
            'last_capture_at': None,
            'last_sync_time': None,
            'last_confidence': None,
            'last_source': 'android_companion',
            'last_fen': None,
            'last_candidate_fen': None,
            'last_candidate_confidence': None,
            'last_reject_reason': '识别助手未安装',
            'stable_frame_count': 0,
            'stable_frame_target': RECOGNITION_SYNC_MIN_STABLE_FRAMES,
            'turn_source': 'fallback_cycle',
            'bottom_side': 'red',
            'frame_width': None,
            'frame_height': None,
            'sync_count': 0,
            'app_id': None,
            'turn_indicator_region': {},
        }
    payload.setdefault('available', recognition_companion is not None)
    with recognition_lock:
        payload.update({
            'connected': bool(payload.get('device_connected')),
            'last_synced_at': recognition_runtime.get('last_sync_time'),
            'last_synced_confidence': recognition_runtime.get('last_confidence'),
            'last_synced_source': recognition_runtime.get('last_source'),
            'last_profile_id': recognition_runtime.get('last_profile_id'),
            'last_synced_fen': recognition_runtime.get('last_fen'),
            'accepted_count': recognition_runtime.get('accepted_count', 0),
            'last_candidate_fen': payload.get('last_candidate_fen') or recognition_runtime.get('last_candidate_fen'),
            'last_candidate_confidence': payload.get('last_candidate_confidence') if payload.get('last_candidate_confidence') is not None else recognition_runtime.get('last_candidate_confidence'),
            'last_reject_reason': payload.get('last_reject_reason') or recognition_runtime.get('last_reject_reason'),
            'stable_frame_count': payload.get('stable_frame_count', recognition_runtime.get('stable_frame_count', 0)),
            'turn_source': payload.get('turn_source') or recognition_runtime.get('turn_source') or 'fallback_cycle',
        })
    if extra:
        payload.update(extra)
    return payload


def _cleanup_engine():
    """."""
    """."""
    try:
        if recognition_companion:
            recognition_companion.stop()
    except Exception:
        pass
    try:
        engine.stop()
    except Exception:
        pass


atexit.register(_cleanup_engine)

@app.route('/')
def index():
    """."""
    """."""
    return render_template('chess.html')

@app.route('/favicon.ico')
def favicon():
    # 娴忚鍣ㄥ彲鑳借姹?/favicon.ico锛岃繖閲岃繑鍥?204 浠ラ伩鍏?404 鍣煶
    return ('', 204)

@app.route('/api/start', methods=['POST'])
def start_engine():
    """启动引擎。"""
    if engine.start():
        return jsonify(build_engine_payload({'success': True, 'message': '引擎已启动'}))
    return jsonify({'success': False, 'message': '引擎启动失败'})

@app.route('/api/stop_analysis', methods=['POST'])
def stop_analysis():
    """中断正在进行的分析，立即释放引擎。"""
    engine.stop_analysis()
    return jsonify({'success': True, 'message': '已中断分析'})

@app.route('/api/reset', methods=['POST'])
def reset_game():
    """重置棋局。"""
    engine.stop_analysis()
    engine.reset()
    return jsonify(build_engine_payload({'success': True, 'message': '棋局已重置'}))

@app.route('/api/move', methods=['POST'])
def add_move():
    """添加一步着法（先中断分析，用缓存校验合法性）。"""
    data = request.json or {}
    move = str(data.get('move', '')).strip()
    if not move:
        return jsonify(build_engine_payload({'success': False, 'message': '着法不能为空'}))
    # 先中断可能正在进行的分析，确保引擎立即可用
    engine.stop_analysis()
    # 用缓存的合法着法校验（缓存在上一次局面变化时已刷新）
    legal_moves = engine.get_legal_moves()
    if move not in legal_moves:
        return jsonify(build_engine_payload({'success': False, 'message': '非法着法'}))
    engine.add_move(move)
    return jsonify(build_engine_payload({'success': True, 'message': f'已走子: {move}'}))

@app.route('/api/undo', methods=['POST'])
def undo():
    """撤销一步着法。"""
    engine.stop_analysis()
    with engine.engine_lock:
        if engine.current_position:
            engine.current_position.pop()
            if engine.is_ready:
                engine._apply_position_unlocked()
            return jsonify(build_engine_payload({'success': True}))
    return jsonify({'success': False, 'message': '没有可撤销的着法'})

@app.route('/api/analyze', methods=['POST'])
def analyze():
    """分析当前局面。"""
    data = request.json or {}
    depth = parse_depth(data.get('depth'), DEFAULT_ANALYZE_DEPTH)

    result = engine.analyze(depth)

    if result['best_move']:
        return jsonify({
            'success': True,
            'best_move': result['best_move'],
            'score': result['score'],
            'depth': result['depth'],
            'pv': result['pv']
        })
    return jsonify({'success': False, 'message': '分析失败'})

@app.route('/api/suggest', methods=['POST'])
def suggest():
    """获取着法建议。"""
    data = request.json or {}
    depth = parse_depth(data.get('depth'), DEFAULT_ANALYZE_DEPTH)
    result = engine.analyze(depth=depth)

    if result['best_move']:
        return jsonify({
            'success': True,
            'best_move': result['best_move'],
            'score': result['score'],
            'depth': result.get('depth', depth),
            'pv': result['pv']
        })
    return jsonify({'success': False, 'message': '无法获取建议'})

@app.route('/api/status', methods=['GET'])
def status():
    """获取引擎状态（读取缓存，不触发引擎操作）。"""
    return jsonify(build_engine_payload())

@app.route('/api/set_fen', methods=['POST'])
def set_fen():
    """根据 FEN 设置局面。"""
    data = request.json or {}
    fen = data.get('fen', '').strip()
    if not fen:
        return jsonify({'success': False, 'message': 'FEN 不能为空'})
    if not is_valid_xiangqi_fen(fen):
        return jsonify({'success': False, 'message': 'FEN 格式无效'})
    engine.set_fen(fen)
    return jsonify(build_engine_payload({'success': True, 'message': '已根据 FEN 设置局面'}))


@app.route('/api/position_sync', methods=['POST'])
def position_sync():
    """."""
    """."""
    data = request.json or {}
    result = apply_position_sync_payload(data)
    return jsonify(result)


@app.route('/api/recognition_status', methods=['GET'])
def recognition_status():
    """."""
    """."""
    return jsonify(build_recognition_payload())


@app.route('/api/recognition/start', methods=['POST'])
def recognition_start():
    """."""
    """."""
    if not recognition_companion:
        return jsonify(build_recognition_payload({'success': False, 'message': '???????????? companion'})), 503
    data = request.json or {}
    launch_scrcpy = bool(data.get('launch_scrcpy', True))
    status = recognition_companion.start(launch_scrcpy=launch_scrcpy)
    return jsonify(build_recognition_payload({
        'success': True,
        'message': 'JJ ???????' if status.get('running') else 'JJ ????????'
    }))


@app.route('/api/recognition/stop', methods=['POST'])
def recognition_stop():
    """."""
    """."""
    if not recognition_companion:
        return jsonify(build_recognition_payload({'success': False, 'message': '?????????'})), 503
    recognition_companion.stop()
    return jsonify(build_recognition_payload({'success': True, 'message': 'JJ ???????'}))


@app.route('/api/recognition/execute_move', methods=['POST'])
def recognition_execute_move():
    """执行自动落子"""
    if not recognition_companion:
        return jsonify({'success': False, 'message': '识别助手未加载或初始化失败'}), 503
    data = request.json or {}
    move = str(data.get('move', '')).strip()
    if not move or len(move) < 4:
        return jsonify({'success': False, 'message': '无效的着法'})
    
    result = recognition_companion.execute_move_on_device(move)
    return jsonify(result)

@app.route('/api/recognition/calibrate', methods=['POST'])
def recognition_calibrate():
    """."""
    """."""
    if not recognition_companion:
        return jsonify(build_recognition_payload({'success': False, 'message': '??????????????'})), 503
    data = request.json or {}
    points = data.get('points') or []
    bottom_side = data.get('bottom_side', 'red')
    try:
        recognition_companion.recalibrate(points=points, bottom_side=bottom_side)
        return jsonify(build_recognition_payload({'success': True, 'message': '??? JJ ???????????'}))
    except Exception as exc:
        return jsonify(build_recognition_payload({'success': False, 'message': f'????: {exc}'}))


@app.route('/api/recognition/frame', methods=['GET'])
def recognition_frame():
    """."""
    """."""
    if not recognition_companion:
        return ('', 204)
    frame = recognition_companion.get_latest_frame_bytes()
    if not frame:
        return ('', 204)
    return Response(frame, mimetype='image/jpeg', headers={'Cache-Control': 'no-store, no-cache, max-age=0'})

@app.route('/api/multi_moves', methods=['POST'])
def multi_moves():
    """多个候选着法（添加中文转换）"""
    data = request.json or {}
    try:
        count = int(data.get('count', 5))
    except (TypeError, ValueError):
        count = 5
    count = min(max(count, 1), 5)
    depth = parse_depth(data.get('depth'), DEFAULT_ANALYZE_DEPTH)

    result = engine.analyze(depth=depth, multipv=count)

    if result.get('moves'):
        # 获取当前 FEN
        current_fen = engine.base_fen or "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w"

        # 为每个着法添加中文转换
        moves_with_chinese = []
        for move in result['moves']:
            move_chinese = uci_to_chinese(move['move'], current_fen)
            moves_with_chinese.append({
                'move': move['move'],
                'move_chinese': move_chinese,  # 添加中文着法
                'score': move.get('score'),
                'pv': move.get('pv', [])
            })

        return jsonify({
            'success': True,
            'moves': moves_with_chinese,
            'depth': result.get('depth', 0)
        })
    return jsonify({'success': False, 'message': '获取候选着法失败'})


# ============================================================
#  移动端 API（悬浮窗 App 专用）
# ============================================================

@app.route('/mobile')
def mobile_page():
    """移动端悬浮窗页面（禁用缓存）"""
    response = make_response(render_template('mobile_float.html'))
    response.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate'
    response.headers['Pragma'] = 'no-cache'
    response.headers['Expires'] = '0'
    return response


@app.route('/mobile/full')
def mobile_full_page():
    """移动端完整版页面（带棋盘）"""
    response = make_response(render_template('mobile_full.html'))
    response.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate'
    response.headers['Pragma'] = 'no-cache'
    response.headers['Expires'] = '0'
    return response


@app.route('/api/mobile/quick_suggest', methods=['POST'])
def mobile_quick_suggest():
    """移动端快速建议（简化版）"""
    data = request.json or {}
    fen = data.get('fen', '').strip()
    depth = parse_depth(data.get('depth'), 15)  # 移动端默认深度15

    # 如果提供了 FEN，设置局面
    if fen and is_valid_xiangqi_fen(fen):
        engine.set_fen(fen)

    # 分析
    result = engine.analyze(depth=depth)

    if result['best_move']:
        # 获取当前 FEN（用于着法转换）
        current_fen = engine.base_fen or "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w"

        # 转换为中文着法
        chinese_move = uci_to_chinese(result['best_move'], current_fen)

        return jsonify({
            'success': True,
            'best_move': result['best_move'],
            'best_move_chinese': chinese_move,  # 添加中文着法
            'score': result['score'],
            'depth': result.get('depth', depth),
            'pv': result.get('pv', [])[:3],  # 只返回前3步变化
            'timestamp': time.strftime('%H:%M:%S')
        })
    return jsonify({'success': False, 'message': '分析失败'})


@app.route('/api/mobile/status', methods=['GET'])
def mobile_status():
    """移动端状态检查（简化版）"""
    state = engine.get_state()
    return jsonify({
        'success': True,
        'engine_ready': state['is_ready'],
        'timestamp': time.strftime('%H:%M:%S')
    })


if __name__ == '__main__':
    runtime = parse_runtime_args()
    hot_reload = bool(runtime.hot_reload)

    if hot_reload:
        app.config["TEMPLATES_AUTO_RELOAD"] = True
        app.jinja_env.auto_reload = True

    print("\n" + "="*60)
    print("     中国象棋 AI 助手 - Web 图形界面")
    print("="*60)
    print("\n正在启动 Web 服务...")
    print(f"运行模式: {'热部署（自动重载）' if hot_reload else '普通模式'}")
    print(f"请在浏览器中打开: http://localhost:{runtime.port}")
    print("\n按 Ctrl+C 停止服务")
    print("="*60 + "\n")

    app.run(
        debug=False,
        use_reloader=hot_reload,
        extra_files=collect_hot_reload_files() if hot_reload else None,
        host=runtime.host,
        port=runtime.port
    )

