#!/usr/bin/env python3
"""Prueba el reproductor real por CDP, sin modificar preferencias ni el JS.

--url sirve para Chromium de escritorio (bridge simulado). Sin --url conecta al
WebView instalado y envuelve temporalmente el bridge, conservando sus mensajes.
Requiere websockets; no mide rendimiento porque activa el diagnóstico JS.
"""
import argparse
import json
import urllib.request
from pathlib import Path

from websockets.sync.client import connect


def run(port, url, report, full):
    pages = json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/json'))
    page = next(p for p in pages if p['type'] == 'page')
    results = []
    counter = 0
    with connect(page['webSocketDebuggerUrl']) as ws:
        def cmd(method, params=None):
            nonlocal counter
            counter += 1
            ws.send(json.dumps({'id': counter, 'method': method, 'params': params or {}}))
            while True:
                result = json.loads(ws.recv(timeout=200))
                if result.get('id') == counter:
                    assert 'error' not in result, result
                    return result

        def ev(expression):
            result = cmd('Runtime.evaluate', {'expression': expression, 'awaitPromise': True, 'returnByValue': True})
            assert 'exceptionDetails' not in result.get('result', {}), result
            return result['result']['result'].get('value')

        if url:
            cmd('Page.navigate', {'url': url})
            ev('new Promise((resolve,reject)=>{let n=0;const id=setInterval(()=>{const s=document.getElementById("status")?.textContent||"";if(window.SignAll&&s.includes("Avatar listo")){clearInterval(id);resolve(true);}else if(++n>600){clearInterval(id);reject("avatar no listo: "+s);}},100);})')
            assert ev('(window.__events=[]);window.__nativeBridge=window.SignAllBridge;window.SignAllBridge={postMessage(msg){window.__events.push(String(msg));return window.__nativeBridge?.postMessage(msg);}};true'), 'No se pudo observar el bridge'
        else:
            assert ev('!!window.SignAll'), 'WebView no listo'
            ev('new Promise((resolve,reject)=>{let n=0;const id=setInterval(()=>{const s=document.getElementById("status").textContent;if(s.includes("Avatar listo")){clearInterval(id);resolve(true);}else if(++n>900){clearInterval(id);reject("avatar no listo: "+s);}},100);})')
            wrapped = ev('window.__events=[];window.__nativeBridge=window.SignAllBridge;window.SignAllBridge={postMessage(msg){window.__events.push(String(msg));return window.__nativeBridge.postMessage(msg);}};window.SignAllBridge!==window.__nativeBridge')
            assert wrapped, 'No se pudo observar el bridge'
        original_avatar = ev('new URL(location.href).searchParams.get("avatar")||"juan"')
        ev('window.SignAll.setDebug(true)')

        def queue(label, ids, speed=.5, reduce=False, append=False, stop=False, change_speed=False):
            items = [{'url': f'signs/{i:04d}_0000_0000.glb', 'type': ('AFFIRMATIVE', 'YES_NO_QUESTION', 'NEGATIVE', 'IMPERATIVE')[k % 4]}
                     for k, i in enumerate(ids)]
            options = {'items': items, 'speed': speed, 'reduce': reduce, 'append': append, 'stop': stop, 'change': change_speed}
            expression = '''(async()=>{
              const o=OPTIONS; window.__events=[];
              window.SignAll.setReduceMotion(o.reduce);window.SignAll.setSpeed(o.speed);
              window.SignAll.setQueue(JSON.stringify(o.items));window.SignAll.play();
              return await new Promise((resolve,reject)=>{
                const started=performance.now(),deadline=started+240000;let exit=false,appended=false,stopped=false,changed=false,finishedAt=null;
                const id=setInterval(()=>{
                  const text=document.getElementById('debug').textContent;
                  const starts=window.__events.filter(x=>x.startsWith('signStart:'));
                  // El avatar puede seguir cargando la seña de arranque cuando la
                  // escena ya avisó "lista"; `play()` es no-op hasta que termine.
                  const finished=window.__events.includes('onPlaybackDone');
                  if(!starts.length&&!finished&&performance.now()-started<30000)window.SignAll.play();
                  if(text.includes('fundición: salida'))exit=true;
                  if(o.change&&!changed&&starts.length===1){
                    window.SignAll.setSpeed(1.5);changed=true;
                    if(window.SignAll.queueState().speed!==o.speed*2){clearInterval(id);reject('velocidad cambió dentro de la seña');return;}
                  }
                  if(exit&&o.append&&!appended){appended=true;window.SignAll.appendQueue(JSON.stringify([{url:'signs/0028_0000_0000.glb',type:'NEGATIVE'}]));}
                  if(exit&&o.stop&&!stopped){stopped=true;window.SignAll.stop();finishedAt=performance.now();}
                  const done=window.__events.filter(x=>x==='onPlaybackDone').length;
                  if(done&&!finishedAt)finishedAt=performance.now();
                  if(finishedAt&&performance.now()-finishedAt>150){
                    clearInterval(id);resolve({starts:starts.map(x=>Number(x.split(':')[1])),done,exit,appended,stopped,changed,
                      playing:document.getElementById('debug').textContent.includes('play: true'),queue:window.SignAll.queueState()});
                  }else if(performance.now()>deadline){clearInterval(id);reject('timeout: '+text);}
                },16);
              });
            })()'''.replace('OPTIONS', json.dumps(options))
            result = ev(expression)
            expected = [k for k, sign in enumerate(ids) if 0 <= sign < 50]
            if append:
                expected.append(len(ids))
            assert result['starts'] == expected, (label, result)
            assert result['done'] >= (0 if stop else 1) and not result['playing'], (label, result)
            if reduce or not expected:
                assert not result['exit'], (label, result)
            else:
                assert result['exit'], (label, result)
            if append:
                assert result['appended']
            if change_speed:
                assert result['changed'] and result['queue']['speed'] == 3
            results.append({'case': label, **result})
            print(label, 'OK', flush=True)

        try:
            queue('distintas y cuatro variantes', [0, 1, 20, 28])
            queue('misma seña repetida', [1, 1])
            queue('append durante salida', [39], append=True)
            queue('stop durante salida', [40], stop=True)
            queue('movimiento reducido', [20, 28], reduce=True)
            queue('cambio de velocidad desde siguiente', [0, 28], change_speed=True)
            queue('último recurso ausente', [0, 9999])
            queue('todos los recursos ausentes', [9999])
            if full:
                for avatar in ('juan', 'pauline'):
                    # `setAvatar` arranca la carga y vuelve enseguida: la señal
                    # real de "listo" es que la barra pase de "Cargando avatar".
                    ev(f'window.SignAll.setAvatar({json.dumps(avatar)})')
                    ev('new Promise((resolve,reject)=>{let n=0;const id=setInterval(()=>{const s=document.getElementById("status").textContent;if(s.includes("Avatar listo")){clearInterval(id);resolve(true);}else if(++n>600){clearInterval(id);reject("avatar no listo: "+s);}},100);})')
                    queue(f'50 señas {avatar}, variantes alternadas', list(range(50)), speed=1.5)
        finally:
            ev('window.SignAll.stop();window.SignAll.setDebug(false);window.SignAll.setReduceMotion(false);window.SignAll.setSpeed(.5)')
            ev(f'window.SignAll.setAvatar({json.dumps(original_avatar)})')
            if not url:
                ev('window.SignAllBridge=window.__nativeBridge;delete window.__nativeBridge;delete window.__events')
        report.write_text(json.dumps(results, indent=2) + '\n')
    return results


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, required=True)
    parser.add_argument('--url')
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--full', action='store_true')
    args = parser.parse_args()
    run(args.port, args.url, args.report, args.full)
