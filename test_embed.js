const { resolveEmbedUrl } = require('./backend/src/resolvers/embedResolver.js');

async function test() {
    const urls = [
        'https://streamwish.to/e/w368i1j521y4', // example streamwish URL
        'https://vidhide.com/e/c932u1m542y8' // example vidhide
    ];

    for (const url of urls) {
        console.log(`Testing: ${url}`);
        const result = await resolveEmbedUrl(url);
        console.log(result);
        console.log('---');
    }
}

test();
