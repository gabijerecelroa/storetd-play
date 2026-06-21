const accounts = ['253698', '901177', '669911'];

async function checkAccounts() {
    console.log("\n=======================================================");
    console.log(" 🩺 ESCÁNER MÉDICO DE USUARIOS (STORETD PLAY) 🩺");
    console.log("=======================================================\n");

    for (let code of accounts) {
        console.log(`➤ ANALIZANDO USUARIO: [ ${code} ]`);
        try {
            // 1. Simular Login de TV
            let authRes = await fetch('http://localhost:5000/auth/activate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ activationCode: code, deviceId: 'escanner-medico-001' })
            }).catch(() => null);

            if (!authRes || authRes.status === 404) {
                authRes = await fetch('http://localhost:5000/auth/status', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ code: code, deviceId: 'escanner-medico-001' })
                });
            }

            const authData = await authRes.json();

            if (!authRes.ok || authData.success === false || authData.status === 'error') {
                console.log(`   ❌ ESTADO EN SUPABASE: BLOQUEADO / VENCIDO`);
                console.log(`   📝 Motivo: ${authData.message || JSON.stringify(authData)}`);
            } else {
                console.log(`   ✅ ESTADO EN SUPABASE: ACTIVO Y AUTORIZADO`);
                
                // 2. Simular Extracción de Video (Gran Hermano)
                const liveUrl = `http://localhost:5000/magma-lite/live/454926.m3u8?code=${code}`;
                const videoRes = await fetch(liveUrl, { method: 'GET' });
                
                if (videoRes.ok) {
                    console.log(`   ✅ CONEXIÓN AL PROVEEDOR (DPLATINO): ÉXITO (HTTP 200)`);
                    console.log(`   💡 Conclusión: La cuenta está perfecta. Si falla en la TV real, es porque el proveedor bloquea por Límite de Pantallas Simultáneas.`);
                } else {
                    console.log(`   ❌ CONEXIÓN AL PROVEEDOR: RECHAZADA (HTTP ${videoRes.status})`);
                    console.log(`   💡 Conclusión: Supabase autoriza la entrada a la app, pero la lista Xtream asignada a este usuario está caída, vencida o lo bloqueó el firewall de Dplatino.`);
                }
            }
        } catch (error) {
            console.log(`   ❌ ERROR DE RED LOCAL: ${error.message}`);
        }
        console.log("-------------------------------------------------------");
    }
}

checkAccounts();
