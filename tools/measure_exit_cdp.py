#!/usr/bin/env python3
"""Mide en el dispositivo cuánto suma la salida final a `onPlaybackDone`."""
import argparse
import json
import urllib.request

from websockets.sync.client import connect


def probe(port, report):
    pages = json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/json'))
    page = next(p for p in pages if p['type'] == 'page')
    rows = []
    with connect(page['webSocketDebuggerUrl']) as ws:
        counter = 0

        def cmd(method, params=None):
            nonlocal counter
            counter += 1
            ws.send(json.dumps({'id': counter, 'method': method, 'params': params or {}}))
            while True:
                result = json.loads(ws.recv(timeout=60))
                if result.get('id') == counter:
                    assert 'error' not in result, result
                    return result

        def ev(expression):
            result = cmd('Runtime.evaluate', {'expression': expression, 'awaitPromise': True, 'returnByValue': True})
            assert 'exceptionDetails' not in result.get('result', {}), result
            return result['result']['result'].get('value')

        ev('window.__nativeBridge=window.SignAIBridge;window.__events=[];window.SignAIBridge={postMessage(m){window.__events.push([String(m),performance.now()]);return window.__nativeBridge?.postMessage(m);}};true')
        try:
            for label, speed, reduce in (('normal', .5, False), ('normal', .5, False),
                                         ('2x', 1.0, False), ('movimiento reducido', .5, True)):
                result = ev('''(async()=>{
                  window.SignAI.stop();window.SignAI.setReduceMotion(RE);window.SignAI.setSpeed(SP);
                  window.__events=[];
                  window.SignAI.setQueue(JSON.stringify([{url:'signs/0028_0000_0000.glb',type:'NEGATIVE'}]));
                  const play=setInterval(()=>window.SignAI.play(),16);
                  return await new Promise((resolve,reject)=>{
                    const id=setInterval(()=>{
                      const start=window.__events.find(e=>e[0].startsWith('signStart:'));
                      const done=window.__events.find(e=>e[0]==='onPlaybackDone');
                      if(start&&done){clearInterval(play);clearInterval(id);resolve({start:start[1],done:done[1],elapsed:(done[1]-start[1])/1000});}
                    },16);
                    setTimeout(()=>{clearInterval(play);clearInterval(id);reject('timeout');},30000);
                  });
                })()'''.replace('RE', 'true' if reduce else 'false').replace('SP', str(speed)))
                rows.append({'case': label, 'speed_setting': speed, **result})
        finally:
            ev('window.SignAI.stop();window.SignAI.setDebug(false);window.SignAI.setReduceMotion(false);window.SignAI.setSpeed(.5);window.SignAIBridge=window.__nativeBridge;delete window.__nativeBridge')
    report.write_text(json.dumps(rows, indent=2) + '\n')
    for row in rows:
        print(row['case'], f"{row['elapsed']:.3f} s de signStart a onPlaybackDone")
    return rows


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, required=True)
    parser.add_argument('--report', required=True)
    from pathlib import Path
    args = parser.parse_args()
    probe(args.port, Path(args.report))
