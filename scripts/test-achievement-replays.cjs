const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {catalogRows, metrics} = require('./analyze-achievement-replays.cjs');

const read = name => JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'config', name), 'utf8'));
const catalog = () => read('j6-achievement-expansion.yaml');
const active = read('j6-achievements.yaml');
const matches = (id, facts) => metrics[id].opportunity(facts) && metrics[id].qualifies(facts);

test('expansion preserves active IDs and rejects malformed or duplicate configurable rules', () => {
    const rules = catalogRows(catalog(), active);
    assert.deepEqual(new Set(rules.map(row => row.tier)), new Set(['EASY', 'MEDIUM', 'HARD']));
    assert.throws(() => catalogRows({...catalog(), groups:[]}, active), /cover all difficulty tabs/);

    const wrongCredit = catalog();
    wrongCredit.creditByUnit.hand.provenance = 'auto-play';
    assert.throws(() => catalogRows(wrongCredit, active), /Invalid expansion credit policy/);

    const duplicate = catalog();
    duplicate.groups[0].entries[0].id = active.tiers.EASY[0].id;
    assert.throws(() => catalogRows(duplicate, active), /Invalid achievement/);

    const unknown = catalog();
    unknown.groups[0].entries[0].metric = 'UNKNOWN';
    assert.throws(() => catalogRows(unknown, active), /Invalid achievement/);

    const translation = catalog();
    const counted = translation.groups.flatMap(group => group.entries).find(row => row.id === 'heads-up-showdown-series');
    counted.ruleRo = counted.ruleRo.replace('{target}', 'trei');
    assert.throws(() => catalogRows(translation, active), /Invalid RO target text/);
});

test('uncontested pot and returned chips never masquerade as a showdown win', () => {
    const pot = {chips:20, winners:[1], eligible:[1]};
    const walk = {seat:1, hand:{showdown:false, street:'PREFLOP', returned:[0, 20]}, won:[pot], score:0};
    assert.equal(matches('PREFLOP_FOLD_WIN', walk), true);
    assert.equal(matches('SHOWDOWN_POT', walk), false);
    assert.equal(matches('POT_WITH_REFUND', walk), true);
    assert.equal(matches('POT_WITH_REFUND', {...walk, won:[]}), false);
});

test('side pot, split and the odd chip use the winning layer and button order', () => {
    const main = {chips:30, winners:[0], eligible:[0, 1, 2]};
    const side = {chips:21, winners:[2, 1], eligible:[1, 2]};
    const hand = {showdown:true, layers:[main, side], boardRank:0};
    const equalBoard = {seat:2, hand, won:[side], score:0};
    assert.equal(matches('SIDE_POT_ONLY', equalBoard), true);
    assert.equal(matches('BOARD_PLAYS_SPLIT', equalBoard), true);
    assert.equal(matches('BOARD_PLAYS_SPLIT', {...equalBoard, score:1}), false);
    assert.equal(matches('ODD_CHIP_SPLIT', equalBoard), true);
    assert.equal(matches('ODD_CHIP_SPLIT', {...equalBoard, seat:1}), false);
    assert.equal(matches('SIDE_POT_ONLY', {...equalBoard, seat:0, won:[main]}), false);
});

test('short-stack credit requires a voluntary all-in and honors the ten-big-blind boundary', () => {
    const hand = {before:[100, 400], bigBlind:10, showdown:true};
    const player = {seat:0, hand, actions:[{allIn:true}], won:[{chips:200, winners:[0]}]};
    assert.equal(matches('SHORT_STACK_ALL_IN_POT', player), true);
    assert.equal(matches('SHORT_STACK_ALL_IN_POT', {...player, actions:[]}), false);
    assert.equal(matches('SHORT_STACK_ALL_IN_POT', {...player, hand:{...hand, before:[101, 399]}}), false);
});

test('postflop pressure credits the winning bet street and preserves the showdown boundary', () => {
    const riverFoldWin = {seat:0, hand:{showdown:false, street:'RIVER'}, actions:[{aggressive:true, street:'RIVER'}], won:[{}]};
    assert.equal(matches('POSTFLOP_AGGRESSION_FOLD_WIN', riverFoldWin), true);
    assert.equal(matches('POSTFLOP_AGGRESSION_FOLD_WIN', {...riverFoldWin, actions:[{aggressive:true, street:'TURN'}]}), false);
    assert.equal(matches('POSTFLOP_AGGRESSION_FOLD_WIN', {...riverFoldWin, hand:{...riverFoldWin.hand, showdown:true}}), false);
});


test('a forced runout gives no river decision credit', () => {
    const settled = {seat:0, reached:3, hand:{showdown:true, street:'RIVER'}, actions:[], won:[{}], score:1};
    assert.equal(matches('RIVER_REACHED', settled), true);
    assert.equal(matches('RIVER_DECISION', settled), false);
    assert.equal(matches('RIVER_DECISION', {...settled, actions:[{street:'RIVER', kind:'CHECK'}]}), true);
});

test('two pressure actions on one street still count as one street', () => {
    const winner = {seat:0, hand:{showdown:true}, score:1, won:[{}]};
    const turn = {street:'TURN', aggressive:true};
    const river = {street:'RIVER', aggressive:true};
    assert.equal(matches('MULTISTREET_BET_SHOWDOWN_POT', {...winner, actions:[turn, turn]}), false);
    assert.equal(matches('MULTISTREET_BET_SHOWDOWN_POT', {...winner, actions:[turn, river]}), true);
    assert.equal(matches('MULTISTREET_BET_SHOWDOWN_POT', {...winner, won:[], actions:[turn, river]}), false);
});

test('a title before heads-up requires the champion and a multiway final hand', () => {
    const title = {seat:0, winner:0, finalActive:3};
    assert.equal(matches('TITLE_WITHOUT_HEADS_UP', title), true);
    assert.equal(matches('TITLE_WITHOUT_HEADS_UP', {...title, seat:1}), false);
    assert.equal(matches('TITLE_WITHOUT_HEADS_UP', {...title, finalActive:2}), false);
});


test('a river all-in can pay a bet or increase it, with different evidence', () => {
    const winner = {seat:0, hand:{showdown:true}, score:1, won:[{}]};
    const action = {street:'RIVER', kind:'ALL_IN', aggressive:false};
    assert.equal(matches('RIVER_CALL_SHOWDOWN_POT', {...winner, actions:[action]}), true);
    assert.equal(matches('RIVER_BET_SHOWDOWN_POT', {...winner, actions:[action]}), false);
    const raised = {...action, aggressive:true};
    assert.equal(matches('RIVER_CALL_SHOWDOWN_POT', {...winner, actions:[raised]}), false);
    assert.equal(matches('RIVER_BET_SHOWDOWN_POT', {...winner, actions:[raised]}), true);
});
