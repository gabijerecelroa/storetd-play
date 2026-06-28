async function testAPI() {
    const url = 'http://tv.m3uts.xyz/player_api.php?username=m&password=m&action=get_live_categories';
    console.log(`\n🔍 GOLPEANDO LA PUERTA DE LA API: ${url}`);

    try {
        const response = await fetch(url, {
            headers: {
                "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 15; moto g84 5G Build/V1TCS35H.88-20-1-6-1)"
            }
        });

        console.log(`✅ ESTADO HTTP: ${response.status} ${response.statusText}`);
        const data = await response.text();
        console.log(`📦 TAMAÑO DE LA RESPUESTA: ${data.length} caracteres`);

        if (data.length > 0) {
            console.log(`\n📄 VISTA PREVIA (Formato JSON de Xtream):\n-------------------------------------------------`);
            console.log(data.substring(0, 600));
            console.log(`-------------------------------------------------\n`);
        }
    } catch (error) {
        console.log(`💥 ERROR: ${error.message}`);
    }
}
testAPI();
