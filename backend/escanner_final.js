const codes = ['253698', '901177', '669911'];

async function test() {
    console.log("\n=======================================================");
    console.log(" 🕵️‍♂️ AUDITOR DE CUENTAS: SUPABASE VS DPLATINO 🕵️‍♂️");
    console.log("=======================================================\n");

    for (let code of codes) {
        console.log(`➤ PROBANDO CÓDIGO: [ ${code} ]`);
        try {
            // 1. Simular Login de TV (Con Mac Address falsa para no ser rechazados)
            let res = await fetch('http://localhost:5000/auth/activate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ activationCode: code, deviceId: 'mac-tv-12345', deviceModel: 'TV Samsung' })
            }).catch(() => null);
            
            let data = await res.json();
            
            // Si ya estaba activada, verificamos el estado
            if (!res.ok) {
                const res2 = await fetch('http://localhost:5000/auth/status', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ code: code, deviceId: 'mac-tv-12345' })
                });
                data = await res2.json();
            }

            if (data.status === 'error' || data.success === false) {
                console.log(`   ❌ ERROR EN SUPABASE: ${data.message}`);
                console.log(`   💡 Acción: Entra a Supabase, revisa este código. Está vencido o no existe.`);
                console.log("-------------------------------------------------------");
                continue;
            }

            console.log(`   ✅ LOGIN SUPABASE OK. (Cliente autorizado)`);

            // 2. Extraer el video directamente desde Dplatino
            console.log(`   ⏳ Pidiendo video a Dplatino para este código...`);
            const liveUrl = `http://localhost:5000/magma-lite/live/454926.m3u8?code=${code}`;
            const videoRes = await fetch(liveUrl);
            
            if (videoRes.ok || videoRes.status === 302 || videoRes.status === 301) {
                console.log(`   ✅ VIDEO DPLATINO OK: ¡La cuenta tiene internet y permisos!`);
            } else {
                console.log(`   ❌ VIDEO DPLATINO RECHAZADO: (Error HTTP ${videoRes.status})`);
                console.log(`   ⚠️ CONCLUSIÓN: Supabase está bien, pero el panel Dplatino bloqueó la cuenta Xtream asociada a este usuario (Vencida, sin saldo o bloqueada por IP).`);
            }
        } catch (e) {
            console.log(`   ❌ ERROR DE RED: ${e.message}`);
        }
        console.log("-------------------------------------------------------");
    }
}
test();
