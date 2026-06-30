const axios = require('axios');
const cheerio = require('cheerio');
const https = require('https');

// Lista de User-Agents comunes para rotar y evitar bloqueos básicos
const USER_AGENTS = [
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0'
];

function getRandomUserAgent() {
    return USER_AGENTS[Math.floor(Math.random() * USER_AGENTS.length)];
}

const httpsAgent = new https.Agent({  
  rejectUnauthorized: false
});

async function fetchHtml(url, customHeaders = {}) {
    try {
        console.log(`[Resolver] Fetching URL: ${url}`);
        const parsedUrl = new URL(url);
        
        const defaultHeaders = {
            'User-Agent': getRandomUserAgent(),
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7',
            'Accept-Language': 'en-US,en;q=0.9,es;q=0.8',
            'Connection': 'keep-alive',
            'Upgrade-Insecure-Requests': '1',
            'Sec-Fetch-Dest': 'document',
            'Sec-Fetch-Mode': 'navigate',
            'Sec-Fetch-Site': 'cross-site',
            'Sec-Fetch-User': '?1',
            'Cache-Control': 'max-age=0',
            'Host': parsedUrl.host,
            'Origin': parsedUrl.origin,
            'Referer': parsedUrl.origin + '/'
        };

        const headers = { ...defaultHeaders, ...customHeaders };

        const response = await axios.get(url, {
            headers,
            timeout: 15000,
            maxRedirects: 5,
            httpsAgent,
            validateStatus: function (status) {
                return status >= 200 && status < 500; // Accept some error codes to parse body (like 403 pages that might contain data)
            }
        });
        
        return response.data;
    } catch (error) {
        console.error(`[Resolver] Error fetching ${url}:`, error.message);
        return null;
    }
}

// Desofuscador básico de Base64
function decodeBase64Urls(text) {
    let decodedText = text;
    // Decodifica atob('...')
    const base64Matches = text.match(/atob\(['"]([^'"]+)['"]\)/g);
    if (base64Matches) {
        for (const match of base64Matches) {
            try {
                const b64 = match.match(/atob\(['"]([^'"]+)['"]\)/)[1];
                const decoded = Buffer.from(b64, 'base64').toString('utf8');
                decodedText = decodedText.replace(match, `"${decoded}"`);
            } catch (e) {}
        }
    }
    return decodedText;
}

// Desofuscador de Hex (\x68\x74...) y Unicode (\u002f)
function decodeEncodedStrings(text) {
    try {
        let decoded = text.replace(/\\x([0-9A-Fa-f]{2})/g, (match, p1) => {
            return String.fromCharCode(parseInt(p1, 16));
        });
        decoded = decoded.replace(/\\u([0-9A-Fa-f]{4})/g, (match, p1) => {
            return String.fromCharCode(parseInt(p1, 16));
        });
        // Desescapar barras \/
        decoded = decoded.replace(/\\\//g, '/');
        return decoded;
    } catch (e) {
        return text;
    }
}

// Desempaquetador recursivo de JS (eval(function(p,a,c,k,e,d)...))
function unpackAll(html) {
    let modifiedHtml = html;
    let iteration = 0;
    let unpackedSomething = true;
    
    // Regex mas robusta para atrapar el packer
    const packerRegex = /eval\s*\(\s*(?:function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*d\s*\).*?split\s*\(\s*'\|'\s*\).*?\s*\)\s*\)\s*(?:\(\s*\))?|function\s*\(\s*h\s*,\s*u\s*,\s*n\s*,\s*t\s*,\s*e\s*,\s*r\s*\).*?split\s*\(\s*'\|'\s*\).*?\s*\)\s*\))/g;
    
    while (unpackedSomething && iteration < 5) { // Max 5 niveles de profundidad
        unpackedSomething = false;
        iteration++;
        
        const matches = modifiedHtml.match(packerRegex) || [];
        // Intentar un enfoque alternativo si el primer regex no matchea pero vemos el formato clásico
        const altPackerRegex = /eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*[a-zA-Z0-9_]+\s*\).*?split\s*\(\s*'\|'\s*\).*?\s*\)\s*\)/g;
        const altMatches = modifiedHtml.match(altPackerRegex) || [];
        
        const allMatches = [...new Set([...matches, ...altMatches])];

        for (let match of allMatches) {
            try {
                let unpacked = unpackSingle(match);
                if (unpacked && unpacked.length > 5) {
                    modifiedHtml = modifiedHtml.replace(match, unpacked);
                    unpackedSomething = true;
                }
            } catch (e) {
                // ignorar errores de unpack individual
            }
        }
    }
    return modifiedHtml;
}

function unpackSingle(code) {
    try {
        // Extraer los argumentos del packer usando regex
        const argsMatch = code.match(/}\s*\(\s*['"](.*?)['"]\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*['"]([^']+)['"]\.split\s*\(\s*'\|'\s*\)/);
        if (!argsMatch) return null;
        
        let [ , p, a, c, k ] = argsMatch;
        a = parseInt(a, 10);
        c = parseInt(c, 10);
        k = k.split('|');
        
        const e = function(c) {
            return (c < a ? '' : e(parseInt(c / a))) + ((c = c % a) > 35 ? String.fromCharCode(c + 29) : c.toString(36));
        };
        
        while (c--) {
            if (k[c]) {
                p = p.replace(new RegExp('\\b' + e(c) + '\\b', 'g'), k[c]);
            }
        }
        return p;
    } catch(err) {
        return null;
    }
}

// Extractor generico para m3u8 / mp4
function extractMediaFromHtml(html) {
    if (!html) return null;
    
    // 1. Limpiar y desofuscar
    html = decodeEncodedStrings(html);
    html = decodeBase64Urls(html);
    html = unpackAll(html);
    // Vuelve a aplicar decode por si el unpack expuso hex codes
    html = decodeEncodedStrings(html);
    
    // 2. Definir patrones de búsqueda desde los más precisos a los más generales
    const patterns = [
        /(?:file|src|url)["'\s:=]+(https?:\/\/[^"'\s]+\.m3u8(?:[^"'\s]*))/i,
        /(https?:\/\/[^"'\s]+(?:master|index|playlist)\.m3u8(?:[^"'\s]*))/i,
        /source\s*:\s*["'](https?:\/\/[^"'\s]+\.m3u8(?:[^"'\s]*))["']/i,
        /['"](https?:\/\/[^"'\s]+\.m3u8(?:[^"'\s]*))['"]/i,
        /(?:file|src|url)["'\s:=]+(https?:\/\/[^"'\s]+\.mp4(?:[^"'\s]*))/i,
        /<source[^>]+src=["'](https?:\/\/[^"'\s]+(?:\.m3u8|\.mp4)[^"'\s]*)["']/i,
        // Algunos servidores guardan la url en window.variable = "url"
        /(?:m3u8|hls|video_url)[^=]*=\s*["'](https?:\/\/[^"'\s]+)["']/i
    ];
    
    // 3. Evaluar patrones
    for (let pattern of patterns) {
        const match = html.match(pattern);
        if (match && match[1]) {
            let url = match[1];
            // Validar que parece una url de verdad y no codigo js corrupto
            if (url.startsWith('http') && url.includes('.')) {
                console.log(`[Resolver] Found media via pattern: ${pattern}`);
                return url;
            }
        }
    }

    // 4. Intentar buscar JSON empotrado
    try {
        const jsonMatch = html.match(/\[\{.*?file\s*:\s*['"](.*?)['"]/);
        if (jsonMatch && jsonMatch[1] && jsonMatch[1].startsWith('http')) {
            console.log(`[Resolver] Found media via JSON pattern`);
            return jsonMatch[1];
        }
    } catch(e){}

    return null;
}

// Resolvers Específicos
async function resolveStreamwish(url) {
    console.log(`[Resolver] Attempting Streamwish: ${url}`);
    let html = await fetchHtml(url);
    if (!html) return null;
    
    // Chequear si es la pantalla de "Loading..." o proteccion de CF
    if (html.includes('Page is loading') || html.includes('cf-browser-verification')) {
        console.log(`[Resolver] Streamwish loading screen detected. Trying API or alternatives...`);
        // Streamwish a veces tiene la m3u8 oculta en otra peticion o se puede obviar cambiando de dominio
        const altUrl = url.includes('.to') ? url.replace('.to', '.com') : url.replace('.com', '.to');
        const altHtml = await fetchHtml(altUrl, { 'Referer': 'https://streamwish.to/' });
        if (altHtml) {
            html = html + " " + altHtml; // Juntar ambos por si acaso
        }
    }

    return extractMediaFromHtml(html);
}

async function resolveVidhide(url) {
    console.log(`[Resolver] Attempting Vidhide: ${url}`);
    const html = await fetchHtml(url);
    return extractMediaFromHtml(html);
}

async function resolveDo7go(url) {
    console.log(`[Resolver] Attempting Do7go: ${url}`);
    const html = await fetchHtml(url);
    return extractMediaFromHtml(html);
}

async function resolveStreamtape(url) {
    console.log(`[Resolver] Attempting Streamtape: ${url}`);
    const html = await fetchHtml(url);
    if (!html) return null;
    
    // Streamtape armar url con variables
    const robotlinkMatch = html.match(/document\.getElementById\(['"]robotlink['"]\)\.innerHTML\s*=\s*['"]([^'"]+)['"]\s*\+\s*\(['"][^'"]+['"][^)]+\)\s*\+\s*['"]([^'"]+)['"]/);
    if (robotlinkMatch) {
        return `https:${robotlinkMatch[1]}${robotlinkMatch[2]}`;
    }
    
    const regexToken = /innerHTML\s*=\s*['"]([^'"]+)['"]\s*\+\s*['"]([^'"]+)['"]/g;
    let m;
    while ((m = regexToken.exec(html)) !== null) {
        if (m[1].includes('get_video')) {
            let stUrl = `https:${m[1]}${m[2]}`;
            // a veces le falta el prefijo
            return stUrl;
        }
    }
    
    return extractMediaFromHtml(html);
}

// Resolver genérico robusto
async function resolveEmbedUrl(embedUrl) {
    let source = "unknown";
    let resolvedUrl = null;
    let message = "No se pudo resolver";
    let success = false;

    try {
        const urlObj = new URL(embedUrl);
        const domain = urlObj.hostname;

        // Mapeo dinámico de dominios para no hacer un if/else gigante
        if (domain.includes('streamwish') || domain.includes('filelions')) {
            source = 'streamwish/filelions';
            resolvedUrl = await resolveStreamwish(embedUrl);
        } else if (domain.includes('vidhide')) {
            source = 'vidhide';
            resolvedUrl = await resolveVidhide(embedUrl);
        } else if (domain.includes('do7go')) {
            source = 'do7go';
            resolvedUrl = await resolveDo7go(embedUrl);
        } else if (domain.includes('streamtape')) {
            source = 'streamtape';
            resolvedUrl = await resolveStreamtape(embedUrl);
        } else if (domain.includes('goodstream') || domain.includes('bysejikuar') || domain.includes('josephseveralconcern')) {
            source = 'generic_known';
            resolvedUrl = await extractMediaFromHtml(await fetchHtml(embedUrl));
        } else {
            source = 'generic_fallback';
            console.log(`[Resolver] Attempting Generic Fallback: ${embedUrl}`);
            resolvedUrl = await extractMediaFromHtml(await fetchHtml(embedUrl));
        }

        if (resolvedUrl) {
            if (resolvedUrl.startsWith('//')) {
                resolvedUrl = 'https:' + resolvedUrl;
            }
            success = true;
            message = "Resuelto correctamente";
            console.log(`[Resolver] Success! Resolved URL: ${resolvedUrl}`);
        } else {
            console.log(`[Resolver] Failed to resolve URL for domain ${domain}.`);
            // Intento final muy agresivo: a veces los servidores hacen redirect 302 y axios no agarra las cookies
            // En un caso real se usaria puppeteer, pero este fallback extractor es lo maximo via HTTP.
        }

    } catch (error) {
        console.error("[Resolver] Error in resolveEmbedUrl:", error.message);
        message = error.message;
    }

    return {
        success,
        resolvedUrl,
        originalUrl: embedUrl,
        source,
        message
    };
}

module.exports = {
    resolveEmbedUrl
};
