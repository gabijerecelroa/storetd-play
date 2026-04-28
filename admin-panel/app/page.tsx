const stats = [
  { label: 'Clientes activos', value: '0' },
  { label: 'Clientes por vencer', value: '0' },
  { label: 'Reportes pendientes', value: '0' },
  { label: 'Dispositivos vinculados', value: '0' },
];

export default function Page() {
  return (
    <main className="dashboard">
      <span className="badge">MVP Admin</span>
      <h1>StoreTD Play Admin</h1>
      <p>Panel base para gestionar clientes, listas autorizadas, EPG, reportes y configuracion comercial.</p>

      <section className="grid">
        {stats.map((stat) => (
          <article className="card" key={stat.label}>
            <span>{stat.label}</span>
            <strong>{stat.value}</strong>
          </article>
        ))}
      </section>

      <section className="grid">
        <article className="card">
          <h2>Clientes</h2>
          <p>Crear, editar, suspender y controlar vencimientos.</p>
        </article>
        <article className="card">
          <h2>Listas M3U</h2>
          <p>Asignar listas autorizadas por cliente.</p>
        </article>
        <article className="card">
          <h2>Reportes</h2>
          <p>Agrupar canales reportados y cambiar estado.</p>
        </article>
        <article className="card">
          <h2>Marca</h2>
          <p>Logo, colores, textos y soporte.</p>
        </article>
      </section>
    </main>
  );
}
