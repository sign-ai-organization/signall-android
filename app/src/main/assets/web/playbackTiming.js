// Salida aprobada en los visores de 2026-11-11: últimos 250 ms en 700 ms.
export const REST_EXIT_TAIL = 0.25;
export const REST_EXIT_SPAN = 0.7;

export function restExitSample(elapsed, duration, begin = Math.max(0, duration - REST_EXIT_TAIL)) {
  const tail = Math.max(0, duration - begin);
  // Mantener Hermite monótona incluso en clips cortos o rutas de error.
  const span = Math.min(REST_EXIT_SPAN, 2.8 * tail);
  const u = span > 0 ? Math.max(0, Math.min(1, elapsed / span)) : 1;
  const time = begin + span * u + (3 * tail - 2 * span) * u * u + (span - 2 * tail) * u * u * u;
  const ease = 6 * u ** 5 - 15 * u ** 4 + 10 * u ** 3;
  return { time: Math.min(duration, time), weight: 1 - ease, done: u >= 1, span };
}
