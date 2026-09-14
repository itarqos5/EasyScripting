// Disposable loopback server only. Run prepare, restart the server, then verify.
const assert = require('node:assert/strict');
const path = require('node:path');
const mineflayer = require(path.join(__dirname, '../.runtime/bot/node_modules/mineflayer'));
const { Vec3 } = require(path.join(__dirname, '../.runtime/bot/node_modules/vec3'));
const phase = process.argv[2];
assert.ok(['prepare', 'verify'].includes(phase), 'Use prepare or verify');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25587, username: 'EasyTest', version: '1.21.8', auth: 'offline' });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const messages = [];
let passed = 0;
bot.on('error', console.error);
bot.on('kicked', console.error);
bot.on('messagestr', text => { messages.push(text); console.log('CHAT', text); });
const command = async text => { bot.chat(text); await pause(450); };
const check = (label, value) => { assert.ok(value, label); console.log('PASS', ++passed, label); };
bot.once('spawn', async () => {
  await pause(1500);
  try {
    if (phase === 'prepare') {
      await command('/clear');
      await command('/actor delete restart_actor');
      await command('/es kit delete restart_kit');
      await command('/scene delete disconnect_fixture');
      await command('/es player hunger 10');
      await command('/es player heal');
      await command('/es item give netherite_sword');
      await command('/es kit save restart_kit');
      await command('/actor create restart_actor ZOMBIE');
      await command('/actor kit restart_actor restart_kit');
      await command('/actor set restart_actor glow on');
      await command('/actor set restart_actor pose SNEAKING');
      await command('/scene create disconnect_fixture');
      await command('/scene add disconnect_fixture 0 health self value=6');
      await command('/scene add disconnect_fixture 600 wait self');
      await command('/scene play disconnect_fixture');
      check('player scene changed health before disconnect (health=' + bot.health + ')', bot.health === 6);
      console.log('PREPARED: restart the server before verify');
    } else {
      check('health restored after disconnect and restart', bot.health === 20);
      const actors = Object.values(bot.entities).filter(entity => entity.name === 'zombie' && entity.equipment?.some(item => item?.name === 'netherite_sword'));
      check('exactly one persisted equipped actor', actors.length === 1);
      check('persisted actor glow', (actors[0].metadata[0] & 0x40) !== 0);
      await command('/scene list');
      check('scene definition persisted', messages.at(-1).includes('disconnect_fixture'));
      await command('/setblock 22 -60 20 stone');
      await command('/es region restore malformed_region');
      check('malformed inventory rejected before any block mutation', bot.blockAt(new Vec3(22, -60, 20))?.name === 'stone' && messages.some(text => text.includes("Region 'malformed_region' stopped:")));
      await command('/actor delete restart_actor');
      await command('/es kit delete restart_kit');
      await command('/scene delete disconnect_fixture');
      await command('/es region delete malformed_region');
      await command('/setblock 22 -60 20 air');
      await command('/es player feed');
      await command('/es status');
      check('restart fixtures release jobs and actors', /actors=0; active jobs=0\b/.test(messages.at(-1)));
      console.log(`RESTART TESTS PASSED: ${passed}`);
    }
    bot.quit();
  } catch (error) { console.error('RESTART TEST FAILED', error); process.exitCode = 1; bot.quit(); }
});
setTimeout(() => { if (!bot._client.ended) { console.error('Restart test timeout'); process.exitCode = 1; bot.quit(); } }, 45000).unref();
