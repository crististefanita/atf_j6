const fs = require('node:fs');
const assert = require('node:assert/strict');

const ranks = '23456789TJQKA';
const suits = 'cdhs';
const sum = xs => xs.reduce((total, value) => total + value, 0);
const list = xs => '[' + xs.join(', ') + ']';

function rankFive(cards) {
  const values = cards.map(card => ranks.indexOf(card[0]) + 2).sort((a, b) => b - a);
  const counts = new Map();
  for (const value of values) counts.set(value, (counts.get(value) ?? 0) + 1);
  const groups = [...counts].sort(([a, ac], [b, bc]) => bc - ac || b - a);
  const shape = groups.map(group => group[1]).join('');
  const straight = groups.length === 5 && (values[0] - values[4] === 4 ? values[0]
    : values.join() === '14,5,4,3,2' ? 5 : 0);
  const flush = cards.every(card => card[1] === cards[0][1]);
  if (flush && straight) return [8, straight];
  if (flush) return [5, ...values];
  if (straight) return [4, straight];
  return [{ 41: 7, 32: 6, 311: 3, 221: 2, 2111: 1, 11111: 0 }[shape], ...groups.map(group => group[0])];
}

function rankSeven(cards) {
  let best = -1;
  for (let a = 0; a < 6; a++) for (let b = a + 1; b < 7; b++) {
    const key = rankFive(cards.filter((_, index) => index !== a && index !== b));
    while (key.length < 6) key.push(0);
    best = Math.max(best, key.reduce((score, value) => score * 15 + value, 0));
  }
  return best;
}

function next(after, predicate, count) {
  for (let offset = 1; offset <= count; offset++) {
    const seat = (after + offset) % count;
    if (predicate(seat)) return seat;
  }
  return -1;
}

function audit(file) {
  const lines = fs.readFileSync(file, 'utf8').trim().split(/\r?\n/);
  const header = /^CHALLENGE id=(\S+) button=(\d+) stacks=([\d,]+) blinds=(\d+),(\d+),(\d+) deckPrefix=([\w,]+)$/.exec(lines[0]);
  assert.ok(header, 'Missing controlled challenge header');
  const [, id, buttonText, stacksText, smallText, bigText, openText, deckText] = header;
  const button = +buttonText, initial = stacksText.split(',').map(Number);
  const [smallBlind, bigBlind, minimumOpen] = [smallText, bigText, openText].map(Number);
  const deck = deckText.split(',');
  assert.equal(deck.length, 20, 'Scenario must include the exact hole cards and all board/burn positions');
  assert.equal(new Set(deck).size, deck.length, 'Duplicate challenge card');
  assert.ok(deck.every(card => ranks.includes(card[0]) && suits.includes(card[1])));
  assert.ok(initial.length >= 2 && initial.length <= 6 && initial.every(chips => Number.isInteger(chips) && chips >= 0));
  const seats = initial.flatMap((chips, seat) => chips > 0 ? [seat] : []);
  const small = seats.length === 2 ? button : next(button, seat => seats.includes(seat), initial.length);
  const big = next(small, seat => seats.includes(seat), initial.length);
  const first = seats.length === 2 ? big : small;
  const hole = new Map(seats.map(seat => [seat, []]));
  for (let round = 0; round < 2; round++) {
    let seat = first;
    for (let index = 0; index < seats.length; index++) {
      hole.get(seat).push(deck[round * seats.length + index]);
      seat = next(seat, candidate => seats.includes(candidate), initial.length);
    }
  }
  const board = [deck[13], deck[14], deck[15], deck[17], deck[19]];
  const stack = [...initial], paid = Array(initial.length).fill(0), streetPaid = Array(initial.length).fill(0);
  const folded = new Set();
  const actedAt = Array(initial.length).fill(null);
  const actions = lines.slice(1).filter(line => line.startsWith('ACTION ')).map(line => {
    const match = /^ACTION seat=(\d+) type=(\w+) amount=(\d+)$/.exec(line);
    assert.ok(match, 'Invalid action record: ' + line);
    return { seat: +match[1], type: match[2], amount: +match[3] };
  });
  assert.ok(actions.length > 0);

  const events = lines.filter(line => line.startsWith('ENGINE ')).map(line => line.slice(7));
  assert.ok(events.length > 0, 'Missing engine transcript');
  const expectEvent = (prefix, expected) => assert.ok(events.includes(expected), `${prefix}: missing ${expected}`);
  expectEvent('seats', 'SEATS ' + list(seats));
  expectEvent('small blind', `SB ${small} ${Math.min(smallBlind, stack[small])}`);
  const postSmall = Math.min(smallBlind, stack[small]);
  stack[small] -= postSmall; paid[small] += postSmall; streetPaid[small] += postSmall;
  expectEvent('big blind', `BB ${big} ${Math.min(bigBlind, stack[big])}`);
  const postBig = Math.min(bigBlind, stack[big]);
  stack[big] -= postBig; paid[big] += postBig; streetPaid[big] += postBig;

  let price = bigBlind, increment = bigBlind, street = 0, after = big;
  const streetNames = ['PREFLOP', 'FLOP', 'TURN', 'RIVER'];
  const live = () => seats.filter(seat => !folded.has(seat));
  const able = () => live().filter(seat => stack[seat] > 0);
  function pending() {
    if (live().length < 2) return -1;
    const actors = able();
    const wager = actors.length === 1 ? Math.max(...streetPaid) : price;
    return next(after, seat => actors.includes(seat) && (streetPaid[seat] < wager
      || (actors.length > 1 && (actedAt[seat] === null || actedAt[seat] < wager))), initial.length);
  }
  const expectedActionEvents = [];
  for (let index = 0; index < actions.length; index++) {
    const { seat, type, amount } = actions[index];
    const actor = pending();
    assert.equal(seat, actor, `Action ${index} actor/order mismatch`);
    assert.ok(amount >= 0 && amount <= stack[seat], `Action ${index} exceeds stack`);
    const due = Math.max(0, price - streetPaid[seat]);
    const mayRaise = able().length > 1 && (actedAt[seat] === null || price - actedAt[seat] >= increment);
    switch (type) {
      case 'FOLD': assert.ok(due > 0 && amount === 0); folded.add(seat); break;
      case 'CHECK': assert.equal(due, 0); assert.equal(amount, 0); break;
      case 'CALL': assert.ok(due > 0 && amount === Math.min(due, stack[seat])); break;
      case 'BET': assert.ok(price === 0 && mayRaise && amount >= increment); break;
      case 'RAISE': assert.ok(price > 0 && mayRaise && amount >= due + increment); break;
      case 'ALL_IN': assert.ok(amount === stack[seat] && (amount <= due || mayRaise)); break;
      default: assert.fail('Unknown action ' + type);
    }
    const oldPrice = price;
    stack[seat] -= amount; paid[seat] += amount; streetPaid[seat] += amount;
    if (streetPaid[seat] > price) {
      const raise = streetPaid[seat] - price;
      if (raise >= increment) increment = raise;
      price = streetPaid[seat];
    }
    assert.ok(price >= oldPrice, 'Wager price must be monotonic within a street');
    actedAt[seat] = price;
    expectedActionEvents.push(`${streetNames[street]} ${seat} ${type} ${amount}`);
    after = seat;
    if (pending() < 0 && live().length > 1 && street < 3) {
      street++;
      streetPaid.fill(0); actedAt.fill(null); price = 0; increment = minimumOpen; after = button;
    }
  }
  assert.equal(pending(), -1, 'Script ended with a live action pending');
  assert.equal(street, 3, 'Full five-card board must be reached');
  assert.deepEqual(events.filter(event => /^(PREFLOP|FLOP|TURN|RIVER) \d+ (FOLD|CHECK|CALL|BET|RAISE|ALL_IN) \d+$/.test(event)),
    expectedActionEvents, 'Actor/action transcript differs or is out of order');
  assert.deepEqual(events.filter(event => /^(FLOP|TURN|RIVER) \[/.test(event)), [
    `FLOP [${board.slice(0, 3).join(', ')}]`, `TURN [${board.slice(0, 4).join(', ')}]`,
    `RIVER [${board.join(', ')}]`
  ], 'Board or burn-card offsets differ from the fixture');

  const ranksBySeat = new Map(live().map(seat => [seat, rankSeven([...hole.get(seat), ...board])]));
  const awards = Array(initial.length).fill(0), expectedPots = [];
  let previous = 0;
  for (const cap of [...new Set(paid.filter(Boolean))].sort((a, b) => a - b)) {
    const contributors = Array.from({ length: initial.length }, (_, offset) => (button + offset + 1) % initial.length)
      .filter(seat => paid[seat] >= cap);
    const layer = (cap - previous) * contributors.length;
    previous = cap;
    if (contributors.length === 1) {
      awards[contributors[0]] += layer;
      expectedPots.push(`RETURN seat=${contributors[0]} amount=${layer}`);
      continue;
    }
    const eligible = contributors.filter(seat => ranksBySeat.has(seat));
    const best = Math.max(...eligible.map(seat => ranksBySeat.get(seat)));
    const winners = eligible.filter(seat => ranksBySeat.get(seat) === best);
    winners.forEach((seat, index) => { awards[seat] += Math.floor(layer / winners.length) + (index < layer % winners.length ? 1 : 0); });
    expectedPots.push(`POT cap=${cap} amount=${layer} eligible=${list(eligible)} winners=${list(winners)}`);
  }
  expectedPots.push(`SHOWDOWN ${list(awards)}`);
  assert.deepEqual(events.filter(event => /^(POT|RETURN|SHOWDOWN|FOLD_WIN) /.test(event)), expectedPots,
    'Independent hand ranking, side-pot eligibility or odd-chip allocation differs');
  const actualStacks = initial.map((chips, seat) => chips - paid[seat] + awards[seat]);
  const finalLine = lines.find(line => line.startsWith('FINAL '));
  assert.ok(finalLine);
  assert.deepEqual(finalLine.slice(13).split(',').map(Number), actualStacks, 'Final stacks differ');
  assert.equal(sum(initial), sum(actualStacks), 'Chip conservation failed');
  const splitPotLayers = expectedPots.filter(line => /^POT /.test(line))
    .filter(line => (line.match(/winners=\[([^\]]+)\]/)?.[1].split(', ').length ?? 0) > 1).length;
  const categoryNames = ['high card', 'one pair', 'two pair', 'three of a kind', 'straight', 'flush', 'full house', 'four of a kind', 'straight flush'];
  const handSummaries = Object.fromEntries([...ranksBySeat].map(([seat, score]) => {
    const fields = Array(6);
    let code = score;
    for (let index = fields.length - 1; index >= 0; index--) { fields[index] = code % 15; code = Math.floor(code / 15); }
    return [seat, { hole: hole.get(seat), category: categoryNames[fields[0]], tieBreakRanks: fields.slice(1), rankCode: score }];
  }));
  return { id, players: initial.length, actions: actions.length, streets: 3, pots: expectedPots.length - 1,
    splitPotLayers,
    board, holeCards: Object.fromEntries([...hole].map(([seat, cards]) => [seat, cards])), handSummaries,
    contributions: paid, settlement: expectedPots, awards, stacks: actualStacks,
    verifiedBy: 'independent JavaScript turn model, seven-card ranker and side-pot resolver' };
}

try {
  const file = process.argv[2];
  if (!file || process.argv.length !== 3) throw new Error('Usage: node scripts/audit-allin-challenge.cjs <challenge-trace>');
  console.log(JSON.stringify(audit(file), null, 2));
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
