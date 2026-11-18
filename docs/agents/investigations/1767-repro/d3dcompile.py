# Copyright 2026, Christopher Banes and the Haze project contributors
# SPDX-License-Identifier: Apache-2.0
"""Ownership probe for Haze #1767: capture the HLSL Skia passes to D3DCompile, then recompile it.

  python d3dcompile.py profile <repro-root> <output> <version>...
      For each version, runs control, retained and original once under d3dcompile-hook.js, using
      <repro-root>/repro-<version>/build/install/windows-glass-repro, then compiles every captured
      shader directly in three fresh processes. These timings are separate from run.ps1's.
  python d3dcompile.py direct <dll> <hlsl> <entry> <target> <flags1> <flags2>
"""
import ctypes
import difflib
import hashlib
import json
import os
import subprocess
import sys
import threading
import time
from pathlib import Path

CASES = ('control', 'retained', 'original')
CAPTURE_TIMEOUT_S = 90
DIRECT_TIMEOUT_S = 120


def capture(java, classpath, case, out):
    import frida

    out.mkdir(parents=True, exist_ok=True)
    calls, modules, hooks, errors = {}, [], [], []
    detached = threading.Event()

    def on_message(message, data):
        if message['type'] != 'send':
            errors.append(message)
            return
        payload = message['payload']
        kind = payload.pop('type')
        if kind == 'module':
            modules.append(payload['path'])
        elif kind == 'hooked':
            hooks.append(payload)
        elif kind == 'enter':
            (out / f"call-{payload['id']}.hlsl").write_bytes(data)
            calls[payload['id']] = {
                **payload, 'sha256': hashlib.sha256(data).hexdigest(), 'lines': data.count(b'\n') + 1,
                'hresult': None, 'ms': None,
            }
        elif kind == 'leave':
            calls[payload['id']].update(hresult=payload['hresult'], ms=payload['ms'])

    device = frida.get_local_device()
    env = {'SKIKO_RENDER_API': 'DIRECT3D', 'SKIKO_HARDWARE_INFO_ENABLED': 'true'}
    pid = device.spawn([java, '-Xmx1g', '-cp', classpath, 'ReproducerKt', case, str(out / 'run')], env=env)
    session = device.attach(pid)
    session.on('detached', lambda *_: detached.set())
    script = session.create_script(Path(__file__).with_name('d3dcompile-hook.js').read_text())
    script.on('message', on_message)
    script.load()
    device.resume(pid)
    censored = not detached.wait(CAPTURE_TIMEOUT_S)
    if censored:
        device.kill(pid)
        detached.wait(10)
    result = {
        'case': case, 'censored': censored, 'hooks': hooks, 'fridaErrors': errors,
        'calls': [calls[k] for k in sorted(calls)], 'compilerModules': [m for m in modules if 'compiler' in m.lower()],
    }
    (out / 'modules.txt').write_text('\n'.join(modules) + '\n')
    (out / 'capture.json').write_text(json.dumps(result, indent=2))
    return result


def direct(dll, hlsl, entry, target, flags1, flags2):
    compiler = ctypes.WinDLL(dll)
    compiler.D3DCompile.restype = ctypes.c_long
    compiler.D3DCompile.argtypes = [
        ctypes.c_void_p, ctypes.c_size_t, ctypes.c_char_p, ctypes.c_void_p, ctypes.c_void_p,
        ctypes.c_char_p, ctypes.c_char_p, ctypes.c_uint, ctypes.c_uint,
        ctypes.POINTER(ctypes.c_void_p), ctypes.POINTER(ctypes.c_void_p),
    ]
    source = Path(hlsl).read_bytes()
    code, messages = ctypes.c_void_p(), ctypes.c_void_p()
    start = time.perf_counter()
    hresult = compiler.D3DCompile(
        source, len(source), None, None, None, entry.encode(), target.encode(), int(flags1), int(flags2),
        ctypes.byref(code), ctypes.byref(messages),
    )
    print(json.dumps({'hresult': hresult, 'ms': (time.perf_counter() - start) * 1000}))


def timing(run, phase):
    path = run / 'timings.csv'
    rows = dict(line.split(',') for line in path.read_text().splitlines()[1:]) if path.exists() else {}
    return float(rows[phase]) if phase in rows else None


def profile(root, output, versions):
    java = str(Path(os.environ['JAVA_HOME']) / 'bin' / 'java.exe')
    summary = []
    for version in versions:
        classpath = str(root / f'repro-{version}' / 'build/install/windows-glass-repro/lib/*')
        for case in CASES:
            out = output / version / case
            result = capture(java, classpath, case, out)
            identity = (out / 'run/identity.txt')
            skiko = next((l[6:] for l in identity.read_text().splitlines() if l.startswith('skiko=')), None) \
                if identity.exists() else None
            dll = next((m for m in (out / 'modules.txt').read_text().splitlines()
                        if m.lower().endswith('d3dcompiler_47.dll')), None)
            for call in result['calls']:
                call['direct'] = []
                if call['defines'] or call['include']:
                    continue  # direct passes NULL defines/include, so it would not reproduce this call
                direct_ms = []
                for _ in range(3):
                    command = [sys.executable, '-I', __file__, 'direct', dll, str(out / f"call-{call['id']}.hlsl"),
                               call['entry'], call['target'], str(call['flags1']), str(call['flags2'])]
                    try:
                        done = subprocess.run(command, capture_output=True, text=True, timeout=DIRECT_TIMEOUT_S)
                        direct_ms.append(json.loads(done.stdout) if done.returncode == 0 else {'error': done.stderr})
                    except subprocess.TimeoutExpired:
                        direct_ms.append({'censored': f'>= {DIRECT_TIMEOUT_S} s'})
                call['direct'] = direct_ms
            run = 'censored' if result['censored'] else 'error' if (out / 'run/error.txt').exists() \
                else 'incomplete' if timing(out / 'run', 'first-draw') is None else 'ok'
            if result['fridaErrors'] or not result['hooks']:
                run += '; hook failed or d3dcompiler_47 not loaded'
            if skiko != version:
                run = f'INVALID: runtime Skiko {skiko}'
            summary.append({
                'version': version, 'skikoRuntime': skiko, 'run': run, 'case': case, 'censored': result['censored'],
                'firstDrawMs': timing(out / 'run', 'first-draw'), 'error': (out / 'run/error.txt').exists(),
                'hooks': result['hooks'], 'compilerModules': result['compilerModules'],
                'fridaErrors': result['fridaErrors'], 'calls': result['calls'],
            })
    (output / 'profile.json').write_text(json.dumps(summary, indent=2))
    # Hooked first draws carry Frida overhead; never copy them into the compare-skiko tables.
    lines = ['| version | run | case | hooked first draw ms | call | entry | target | flags | bytes | lines | '
             'in-process ms (hresult) | direct ms (3 fresh processes) |',
             '| --- | --- | --- | ---: | ---: | --- | --- | --- | ---: | ---: | ---: | --- |']
    for row in summary:
        first = 'censored' if row['censored'] else row['firstDrawMs']
        if not row['calls']:
            lines.append(f"| {row['version']} | {row['run']} | {row['case']} | {first} | none | | | | | | | |")
        for call in row['calls']:
            direct_ms = ', '.join(
                (f"{d['ms']:.1f}" if d['hresult'] == 0 else f"error {d['hresult'] & 0xffffffff:#x}") if 'ms' in d
                else d.get('censored', 'error') for d in call['direct']) or 'not reproduced (defines/include)'
            in_process = f"{call['ms']} ({call['hresult'] & 0xffffffff:#x})" if call['ms'] is not None \
                else 'running at kill'
            lines.append(
                f"| {row['version']} | {row['run']} | {row['case']} | {first} | {call['id']} | {call['entry']} | "
                f"{call['target']} | {call['flags1']:#x}/{call['flags2']:#x} | {call['size']} | {call['lines']} | "
                f"{in_process} | {direct_ms} |")
    lines += ['', 'Compiler modules: ' + '; '.join(sorted({m for r in summary for m in r['compilerModules']}))]
    if len(versions) == 2:
        for case in CASES:
            old, new = (output / v / case for v in versions)
            for hlsl in sorted(old.glob('call-*.hlsl')):
                other = new / hlsl.name
                if other.exists() and other.read_bytes() != hlsl.read_bytes():
                    diff = difflib.unified_diff(hlsl.read_text().splitlines(), other.read_text().splitlines(),
                                                f'{versions[0]}/{case}/{hlsl.name}', f'{versions[1]}/{case}/{hlsl.name}',
                                                lineterm='')
                    (output / f'{case}-{hlsl.stem}.diff').write_text('\n'.join(diff) + '\n')
                    lines.append(f'HLSL differs between versions: {case}/{hlsl.name}')
    (output / 'profile.md').write_text('\n'.join(lines) + '\n')
    print('\n'.join(lines))


if __name__ == '__main__':
    if sys.argv[1:2] == ['direct']:
        direct(*sys.argv[2:])
    elif sys.argv[1:2] == ['profile']:
        profile(Path(sys.argv[2]), Path(sys.argv[3]), sys.argv[4:])
    else:
        sys.exit(__doc__)
