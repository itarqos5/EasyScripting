// Run only with the smoke-test plugin on the disposable loopback Paper fixture.
const assert = require('node:assert/strict');
const { once } = require('node:events');
const path = require('node:path');
const mineflayer = require(path.join(__dirname, '../.runtime/bot/node_modules/mineflayer'));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25587, username: 'EasyTest', version: '1.21.8', auth: 'offline' });
const messages = [];
let passed = 0;
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const check = (name, valid) => { assert.ok(valid, name); console.log('PASS', ++passed, name); };
bot.on('messagestr', text => { messages.push(text); console.log('CHAT', text); });
bot.on('error', error => console.error(error));
const command = async text => { bot.chat(text); await pause(450); };
async function serverCheck(stage) {
  const start = messages.length;
  bot.chat('/esactingtest ' + stage);
  for (let i = 0; i < 60; i++) {
    await pause(150);
    const recent = messages.slice(start);
    assert.ok(!recent.some(text => text.includes('ACTING TEST FAILED')), recent.join('\n'));
    if (recent.some(text => text.includes('ACTING CHECK PASSED ' + stage))) { check('server ' + stage, true); return; }
  }
  throw new Error('Server check timed out: ' + stage);
}
async function open(section = 'overview') {
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  const opened = once(bot, 'windowOpen', { signal: AbortSignal.timeout(5000) });
  bot.chat('/actor gui acting_fixture ' + section); await opened;
}
async function click(slot, opens = true) {
  const opened = opens ? once(bot, 'windowOpen', { signal: AbortSignal.timeout(12000) }) : null;
  await bot.clickWindow(slot, 0, 0);
  if (opened) await opened;
  await pause(150);
}
bot.once('spawn', async () => {
  // Paper profile refreshes send a respawn packet without resending existing chunks.
  // Mineflayer drops its chunk cache for that packet, so drive this fixture with known
  // server teleports instead of letting its physics simulate through missing terrain.
  bot.physicsEnabled = false;
  try {
    await pause(2500);
    if (process.argv[2] === 'verify') {
      await serverCheck('restored');
      await command('/actor info acting_fixture');
      check('recording selection and mode persist', messages.some(t => t.includes('recording=acting_disconnect; mode=reverse')));
      const replayStart = messages.length;
      await command('/actor play acting_fixture'); await pause(800);
      await command('/actor set acting_fixture name Tampered');
      check('saved recording can replay after restart', messages.slice(replayStart).some(t => t.includes('busy with recording acting_disconnect')));
      await command('/actor stop acting_fixture');
      await serverCheck('cleanup');
      for (const id of ['acting_take', 'acting_disconnect']) await command('/es record delete ' + id);
      console.log('ACTING CLIENT RESTART PASSED:', passed); bot.quit(); return;
    }
    await command('/tp @s 0.5 -60 0.5');
    for (const id of ['acting_take', 'acting_disconnect']) await command('/es record delete ' + id);
    await serverCheck('setup'); await pause(2500);
    await open();
    check('four actor GUI sections', [10,12,14,16].map(s => bot.currentWindow.slots[s]?.name).join(',') === 'player_head,compass,armor_stand,iron_sword');
    await click(10);
    check('appearance section opens', bot.currentWindow.slots[20]?.name === 'ender_eye' && bot.currentWindow.slots[16]?.name === 'name_tag');
    await click(49); await click(16);
    check('combat section opens', bot.currentWindow.slots[10]?.name === 'target' && bot.currentWindow.slots[12]?.name === 'totem_of_undying');
    await click(10); await serverCheck('unhittable');
    await click(10); await serverCheck('hittable'); await serverCheck('immortal');
    await click(12); await serverCheck('mortal');
    await click(16); await serverCheck('respawned'); await click(12);
    bot.closeWindow(bot.currentWindow);
    await command('/actor act acting_fixture acting_take'); await serverCheck('active');
    const conflictStart = messages.length;
    await command('/actor respawn acting_fixture');
    await command('/scene create acting_conflict');
    await command('/scene add acting_conflict 20 wait self');
    await command('/scene play acting_conflict');
    await command('/es camera move 0 -60 0 20');
    check('acting rejects actor, scene and camera conflicts', messages.slice(conflictStart).filter(t => t.includes('busy with acting')).length === 3);
    await command('/scene delete acting_conflict');
    await command('/tp @s 7.5 -59 0.5');
    await command('/tp @s 8.5 -60 0.5');
    bot.swingArm('right');
    await command('/item replace entity @s armor.chest with iron_chestplate');
    await serverCheck('cue');
    await pause(500); await command('/actor finish');
    await serverCheck('restored'); await serverCheck('saved');
    const frames = Number(messages.findLast(t => t.includes('ACTING FRAMES ')).match(/ACTING FRAMES (\d+)/)[1]);
    const duration = frames * 50 + 450;
    await command('/actor mode acting_fixture stop'); await command('/actor play acting_fixture');
    await pause(duration); await serverCheck('end');
    for (const mode of ['repeat','reverse']) {
      await open('acting'); await click(mode === 'repeat' ? 20 : 21);
      check('mode GUI shows ' + mode, JSON.stringify(bot.currentWindow.slots[16]).includes(mode));
      await click(16, false); await pause(duration * 2);
      const start = messages.length;
      await command('/actor set acting_fixture name Tampered');
      check(mode + ' remains active across recording boundaries', messages.slice(start).some(t => t.includes('busy with recording')));
      await command('/actor stop acting_fixture');
    }
    await open('acting'); await click(10, false); await pause(500);
    check('GUI act starts a new performance', messages.slice(-3).some(t => t.includes("Acting as 'acting_fixture'")));
    await open('acting'); await click(14, false); await serverCheck('restored');
    await command('/actor act acting_fixture acting_disconnect');
    check('disconnect fixture began', messages.slice(-3).some(t => t.includes("Acting as 'acting_fixture'")));
    await pause(500);
    console.log('ACTING CLIENT PREPARE PASSED:', passed, 'Restart the server, then run with verify.');
    bot.quit();
  } catch (error) { console.error('ACTING CLIENT FAILED', error); bot.quit(); process.exitCode = 1; }
});
setTimeout(() => { if (!bot._client.ended) { console.error('Acting client timeout'); bot.quit(); process.exitCode = 1; } }, 180000).unref();
