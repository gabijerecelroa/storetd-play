const { resolveEmbedUrl } = require('./backend/src/resolvers/embedResolver.js');

async function run() {
    const res = await resolveEmbedUrl('https://streamwish.to/e/w368i1j521y4');
    console.log(res);
}
run();
