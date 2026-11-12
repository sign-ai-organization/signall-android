import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

// El proyecto Android no define package.json: cargar el módulo puro como ESM.
const source = await readFile(new URL('../app/src/main/assets/web/playbackTiming.js', import.meta.url), 'utf8');
const { restExitSample } = await import(`data:text/javascript,${encodeURIComponent(source)}`);
for (const duration of [0.01, 0.1, 0.25, 1.4166667, 2.4583333, 4]) {
  const begin = Math.max(0, duration - .25);
  const first = restExitSample(0, duration);
  assert.equal(first.time, begin);
  assert.equal(first.weight, 1);
  let previousTime = begin, previousWeight = 1;
  for (let i = 0; i <= 1000; i++) {
    const value = restExitSample(first.span * i / 1000, duration);
    assert.ok(value.time >= previousTime - 1e-10 && value.time <= duration + 1e-10);
    assert.ok(value.weight >= 0 && value.weight <= previousWeight + 1e-10);
    previousTime = value.time;
    previousWeight = value.weight;
  }
  const end = restExitSample(first.span + 1e-8, duration);
  assert.equal(end.done, true);
  assert.equal(end.weight, 0);
  assert.ok(Math.abs(end.time - duration) < 1e-10);
}
// Igualdad con la función aprobada del visor para clips normales.
for (let i = 0; i <= 1000; i++) {
  const u = i / 1000, duration = 2.4583333, begin = duration - .25;
  const expected = begin + .7*u + (3*.25-2*.7)*u*u + (.7-2*.25)*u*u*u;
  assert.ok(Math.abs(restExitSample(.7*u, duration).time - expected) < 1e-10);
}
console.log('Salida: seis duraciones, 1001 muestras por duración; función aprobada idéntica.');
