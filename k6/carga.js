import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const baseUrl = __ENV.BASE_URL || 'http://back:8080';
const users = JSON.parse(open(__ENV.USERS_FILE || '/scripts/data/usuarios.json'));

export const options = {
  scenarios: {
    carga: {
      executor: 'constant-vus',
      vus: 50,
      duration: '5m',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    'http_req_duration{operation:plano}': ['p(95)<500'],
    'http_req_duration{operation:mis_reservas}': ['p(95)<500'],
    'http_req_duration{operation:crear_reserva}': ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

let reservationAttempted = false;
let vuStartedAt;

export default function () {
  const user = users[exec.vu.idInTest - 1];
  if (!user) {
    throw new Error(`No hay datos para el VU ${exec.vu.idInTest}; hacen falta 50 usuarios.`);
  }

  const params = {
    headers: {
      Authorization: `Bearer ${user.token}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    timeout: '10s',
  };

  if (vuStartedAt === undefined) {
    vuStartedAt = Date.now();
  }

  const planQuery = `fecha=${encodeURIComponent(user.fecha)}&desde=${user.desde}&hasta=${user.hasta}`;
  const plan = http.get(`${baseUrl}/api/pisos/${user.pisoId}/plano?${planQuery}`, {
    ...params,
    tags: { operation: 'plano' },
  });
  check(plan, { 'consultar plano responde 200': (r) => r.status === 200 });

  const misReservas = http.get(`${baseUrl}/api/reservas/mias`, {
    ...params,
    tags: { operation: 'mis_reservas' },
  });
  check(misReservas, { 'consultar Mis reservas responde 200': (r) => r.status === 200 });

  // Spread one unique booking per user across the five-minute run (about 6s apart).
  // Reads continue while each VU waits for its assigned booking time.
  const bookingOffsetMs = (exec.vu.idInTest - 1) * (5 * 60 * 1000 / 50);
  if (!reservationAttempted && Date.now() - vuStartedAt >= bookingOffsetMs) {
    reservationAttempted = true;
    const response = http.post(
      `${baseUrl}/api/reservas`,
      JSON.stringify({
        espacioId: user.espacioId,
        inicio: `${user.fecha}T${user.desde}:00`,
        fin: `${user.fecha}T${user.hasta}:00`,
      }),
      { ...params, tags: { operation: 'crear_reserva' } },
    );
    check(response, { 'crear reserva responde 201': (r) => r.status === 201 });
  }

  // Two reads per second per VU stay below the general 200 requests/minute/user limit.
  sleep(1);
}
