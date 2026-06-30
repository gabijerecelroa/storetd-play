const { resolveEmbedUrl } = require('./embedResolver');

async function test() {
    const urlsToTest = [
        // Ejemplos genericos para ver que no crashea
        'https://streamwish.to/e/example123',
        'https://vidhidefast.com/v/example123'
    ];

    for (const url of urlsToTest) {
        console.log(`\nTesting: ${url}`);
        const result = await resolveEmbedUrl(url);
        console.log(result);
    }
}

test();
