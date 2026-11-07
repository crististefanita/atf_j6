const fs = require('node:fs');
const assert = require('node:assert/strict');

const streets = { PREFLOP: 0, FLOP: 3, TURN: 4, RIVER: 5 };
const cardList = value => value === '-' ? [] : value.split(',');
const mod = (value, divisor) => ((value % divisor) + divisor) % divisor;

function audit(path) {
  const runs = new Map();
  let current;
  for (const [lineIndex, line] of fs.readFileSync(path, 'utf8').split(/\r?\n/).entries()) {
    if (!line) continue;
    const fields = line.split('|');
    const lineNo = lineIndex + 1;
    if (fields[0] === 'ROTATION') {
      assert.equal(current, undefined, `${path}:${lineNo} started before prior hand was revealed`);
      assert.equal(fields.length, 6, `${path}:${lineNo} rotation header must not expose card data`);
      const [, scenario, style, buttonRaw, heroRaw, stackRaw] = fields;
      const button = Number(buttonRaw), hero = Number(heroRaw), startingStack = Number(stackRaw);
      assert.ok(Number.isInteger(button) && button >= 0 && button < 6);
      assert.equal(hero, 2);
      current = { scenario, style, button, hero, startingStack, decisions: [], reveal: undefined };
      const key = `${scenario}/${style}/b${button}`;
      assert.ok(!runs.has(key), `${path}:${lineNo} duplicate run ${key}`);
      runs.set(key, current);
      continue;
    }
    if (fields[0] === 'DECISION') {
      assert.ok(current, `${path}:${lineNo} decision outside a run`);
      assert.equal(current.reveal, undefined, `${path}:${lineNo} decision was logged after reveal`);
      assert.equal(fields.length, 14, `${path}:${lineNo} unexpected decision fields`);
      const [, id, seatRaw, posRaw, street, holeRaw, visibleBoardRaw, potRaw, toCallRaw,
        stackRaw, legalRaw, action, amountRaw, policyVersion] = fields;
      assert.equal(id, `${current.scenario}/${current.style}/b${current.button}`);
      const seat = Number(seatRaw), position = Number(posRaw), amount = Number(amountRaw);
      assert.ok(Number.isInteger(seat) && seat >= 0 && seat < 6);
      assert.equal(position, mod(seat - current.button, 6), `${path}:${lineNo} position/button mismatch`);
      assert.ok(Object.hasOwn(streets, street), `${path}:${lineNo} unknown street ${street}`);
      assert.ok(policyVersion.length > 0);
      const legal = legalRaw.split(',').map(item => {
        const [type, minRaw, maxRaw] = item.split(':');
        return { type, min: Number(minRaw), max: Number(maxRaw) };
      });
      assert.ok(legal.some(item => item.type === action && amount >= item.min && amount <= item.max),
        `${path}:${lineNo} chose an action outside the observed legal set`);
      const decision = { seat, position, street, hole: cardList(holeRaw), board: cardList(visibleBoardRaw),
        pot: Number(potRaw), toCall: Number(toCallRaw), stack: Number(stackRaw), action, amount, policyVersion };
      assert.equal(decision.hole.length, 2, `${path}:${lineNo} must expose only actor's two private cards`);
      assert.equal(decision.board.length, streets[street], `${path}:${lineNo} leaked an undealt board card`);
      current.decisions.push(decision);
      continue;
    }
    if (fields[0] === 'REVEAL') {
      assert.ok(current, `${path}:${lineNo} reveal outside a run`);
      assert.equal(current.reveal, undefined, `${path}:${lineNo} duplicate reveal`);
      assert.equal(fields.length, 9, `${path}:${lineNo} malformed terminal reveal`);
      const [, id, scenario, buttonRaw, heroRaw, rawHands, rawFixtureBoard, rawActualBoard, netRaw] = fields;
      assert.equal(id, `${current.scenario}/${current.style}/b${current.button}`);
      assert.equal(scenario, current.scenario);
      assert.equal(Number(buttonRaw), current.button);
      assert.equal(Number(heroRaw), current.hero);
      const hands = rawHands.split('/').map(cardList);
      const fixtureBoard = cardList(rawFixtureBoard), actualBoard = cardList(rawActualBoard);
      assert.equal(hands.length, 6);
      assert.ok(hands.every(hand => hand.length === 2));
      assert.equal(fixtureBoard.length, 5);
      assert.deepEqual(actualBoard, fixtureBoard.slice(0, actualBoard.length), `${path}:${lineNo} board differs from fixture`);
      const allCards = [...hands.flat(), ...fixtureBoard];
      assert.equal(new Set(allCards).size, allCards.length, `${path}:${lineNo} duplicate fixture card`);
      for (const decision of current.decisions) {
        assert.deepEqual(decision.hole, hands[decision.seat], `${path}:${lineNo} actor projection contains wrong private cards`);
        assert.deepEqual(decision.board, fixtureBoard.slice(0, decision.board.length),
          `${path}:${lineNo} actor projection contains wrong public board prefix`);
      }
      current.reveal = { hands, fixtureBoard, actualBoard, heroNet: Number(netRaw) };
      current = undefined;
      continue;
    }
    assert.fail(`${path}:${lineNo} unknown record ${fields[0]}`);
  }
  assert.equal(current, undefined, `${path} ended before terminal reveal`);
  assert.equal(runs.size, 84, 'Expected two deals × seven policies × six button positions');

  const scenarios = [...new Set([...runs.values()].map(run => run.scenario))];
  const styles = [...new Set([...runs.values()].map(run => run.style))];
  assert.equal(scenarios.length, 2);
  assert.deepEqual(styles.sort(), ['bluff', 'bluff-always', 'calling', 'equity', 'loose', 'pressure', 'tag']);
  for (const scenario of scenarios) {
    const canonical = runs.get(`${scenario}/tag/b0`).reveal;
    for (const run of runs.values()) {
      if (run.scenario !== scenario) continue;
      assert.deepEqual(run.reveal.hands, canonical.hands, `${scenario} changed private cards between rotations`);
      assert.deepEqual(run.reveal.fixtureBoard, canonical.fixtureBoard, `${scenario} changed board between rotations`);
    }
    for (const style of styles) {
      const positions = [...runs.values()].filter(run => run.scenario === scenario && run.style === style)
        .map(run => mod(run.hero - run.button, 6)).sort((a, b) => a - b);
      assert.deepEqual(positions, [0, 1, 2, 3, 4, 5], `${scenario}/${style} did not cover every hero position`);
    }
  }

  const byStyle = {};
  for (const style of styles) {
    const bucket = byStyle[style] = { runs: 0, netChips: 0, netVsTagControl: 0, byHeroPosition: {} };
    for (const run of runs.values()) {
      if (run.style !== style) continue;
      const control = runs.get(`${run.scenario}/tag/b${run.button}`);
      const position = mod(run.hero - run.button, 6);
      const pos = bucket.byHeroPosition[position] ??= { runs: 0, netChips: 0, netVsTagControl: 0, heroActions: {} };
      const heroActions = run.decisions.filter(decision => decision.seat === run.hero);
      for (const decision of heroActions) pos.heroActions[decision.action] = (pos.heroActions[decision.action] ?? 0) + 1;
      const delta = run.reveal.heroNet - control.reveal.heroNet;
      bucket.runs++; bucket.netChips += run.reveal.heroNet; bucket.netVsTagControl += delta;
      pos.runs++; pos.netChips += run.reveal.heroNet; pos.netVsTagControl += delta;
    }
  }
  return { fixedDealRotations: runs.size, deals: scenarios, positionsPerDealAndStyle: 6,
    stylesComparedToSameSeatTag: byStyle,
    caveat: 'These fixed-deal spot checks use passive calling-station opponents to expose limp and checked-to contexts. They validate information boundaries, action legality and position sensitivity; they are not a statistically meaningful estimate of tournament strength.' };
}

if (require.main === module) {
  try {
    const input = process.argv[2];
    if (!input || process.argv.length !== 3) throw new Error('Usage: node scripts/audit-position-rotations.cjs position-rotations.psv');
    console.log(JSON.stringify(audit(input), null, 2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

module.exports = {audit};
