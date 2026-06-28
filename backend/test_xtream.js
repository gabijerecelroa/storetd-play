async function testConexion() {
    const url = 'http://tv.m3uts.xyz/player_api.php?username=m&password=m';
    console.log(`\n🔍 INICIANDO RADIOGRAFÍA DEL SERVIDOR: tv.m3uts.xyz`);
    
    try {
        const response = await fetch(url, {
            headers: {
                "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 15; moto g84 5G Build/V1TCS35H.88-20-1-6-1)"
            },
            redirect: 'follow'
        });

        console.log(`✅ ESTADO HTTP DEVUELTO: ${response.status} ${response.statusText}`);
        
        const text = await response.text();
        console.log(`📦 TAMAÑO DE LA DESCARGA: ${text.length} caracteres`);

        if (text.length > 0) {
            console.log(`\n📄 VISTA PREVIA DE LO QUE MANDÓ EL SERVIDOR:\n-------------------------------------------------`);
            console.log(text.substring(0, 600));
            console.log(`-------------------------------------------------\n`);
        } else {
            console.log(`\n❌ ERROR: El servidor de Xtream Codes contestó, pero mandó un archivo vacío.`);
        }
    } catch (error) {
        console.log(`\n💥 ERROR CRÍTICO DE CONEXIÓN: ${error.message}`);
    }
}
testConexion();
