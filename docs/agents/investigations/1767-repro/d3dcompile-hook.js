// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

// Frida 17 script: records each d3dcompiler_47 D3DCompile/D3DCompile2 call. The HLSL is sent on
// entry so a call still running when the watchdog kills the process keeps its source.
let next = 0;

function str(p) {
  return p.isNull() ? null : p.readUtf8String();
}

function hook(module) {
  for (const name of ['D3DCompile', 'D3DCompile2']) {
    const address = module.findExportByName(name);
    if (address === null) continue;
    Interceptor.attach(address, {
      onEnter(args) {
        this.id = next++;
        this.start = Date.now();
        const size = args[1].toUInt32();
        send({
          type: 'enter', id: this.id, api: name, size, sourceName: str(args[2]),
          defines: !args[3].isNull(), include: !args[4].isNull(), entry: str(args[5]),
          target: str(args[6]), flags1: args[7].toUInt32(), flags2: args[8].toUInt32(),
          startEpochMs: this.start,
        }, args[0].readByteArray(size));
      },
      onLeave(result) {
        send({ type: 'leave', id: this.id, hresult: result.toInt32(), ms: Date.now() - this.start });
      },
    });
    send({ type: 'hooked', api: name, module: module.path });
  }
}

Process.attachModuleObserver({
  onAdded(module) {
    send({ type: 'module', name: module.name, path: module.path });
    if (module.name.toLowerCase() === 'd3dcompiler_47.dll') hook(module);
  },
});
