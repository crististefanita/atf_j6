const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const contextPattern = /^DECISION seat=(\d+) policy=(\S+) action=([A-Z_]+) amount=(\d+) reason=.* positionFromButton=(\d+) street=(PREFLOP|FLOP|TURN|RIVER) pot=(\d+) toCall=(\d+) heroStack=(\d+) activeOpponents=(\d+) bigBlind=(\d+) visibleSeatIds=\[([0-5,]*)\] visibleStacks=\[([0-9,]*)\] visibleContributions=\[([0-9,]*)\] foldedSeatIds=\[([0-5,]*)\] priorAllInActors=\[([0-5,]*)\] priorAllInEvents=\[([0-5:callraise,]*)\] legalActions=(.*)$/;
const numbers = text => text ? text.split(',').map(Number) : [];
const average = values => values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
const standardError = values => {
  if (values.length < 2) return null;
  const mean = average(values);
  return Math.sqrt(values.reduce((sum, value) => sum + (value - mean) ** 2, 0) / (values.length - 1) / values.length);
};

function walk(root) {
  return fs.readdirSync(root, { withFileTypes: true }).flatMap(entry => {
    const resolved = path.join(root, entry.name);
    return entry.isDirectory() ? walk(resolved) : entry.name.endsWith('.trace') ? [resolved] : [];
  });
}

function parseTrace(file, root) {
  const relative = path.relative(root, file).split(path.sep);
  assert.equal(relative.length, 3, `${file}: expected profile/seat-count/trace layout`);
  const profile = relative[0];
  const seatsFolder = /^s([4-6])$/.exec(relative[1]);
  assert.ok(['standard', 'stress'].includes(profile), `${file}: unknown profile`);
  assert.ok(seatsFolder, `${file}: invalid seat-count directory`);
  const seats = +seatsFolder[1];
  let game;
  const games = [];
  for (const [index, line] of fs.readFileSync(file, 'utf8').split(/\r?\n/).entries()) {
    let match = /^TOURNAMENT seed=(-?\d+) seats=(\d+) buttonOffset=(-?\d+) firstButton=(\d+) secondsPerHand=(\d+) rules=Rules\[seats=\d+, startingStack=(\d+), levelSeconds=(\d+), .* policyOffset=(-?\d+)$/.exec(line);
    if (match) {
      assert.equal(game, undefined, `${file}:${index + 1}: nested tournament`);
      assert.equal(+match[2], seats, `${file}:${index + 1}: seat directory mismatch`);
      game = { profile, seats, seed: +match[1], buttonOffset: +match[3], firstButton: +match[4],
        secondsPerHand: +match[5], startingStack: +match[6], levelSeconds: +match[7], policyOffset: +match[8],
        roles: new Map(), hands: [], currentHand: undefined, winner: undefined };
      continue;
    }
    if (!game || !line) continue;
    match = /^STRATEGY seat=(\d+) id=([a-z-]+) implementation=([A-Za-z0-9_$]+)$/.exec(line);
    if (match) {
      const seat = +match[1];
      assert.ok(seat >= 0 && seat < seats && !game.roles.has(seat), `${file}:${index + 1}: duplicate strategy seat`);
      assert.ok(![...game.roles.values()].some(role => role.id === match[2]), `${file}:${index + 1}: duplicate strategy role`);
      game.roles.set(seat, { id: match[2], implementation: match[3] });
      continue;
    }
    match = /^HAND (\d+) .* dealSeed=(-?\d+)$/.exec(line);
    if (match) {
      game.currentHand = { number: +match[1], dealSeed: match[2], decisions: [], stacks: undefined };
      game.hands.push(game.currentHand);
      continue;
    }
    match = contextPattern.exec(line);
    if (match) {
      const seat = +match[1];
      const role = game.roles.get(seat);
      assert.ok(role, `${file}:${index + 1}: decision has no strategy assignment`);
      const visibleSeatIds = numbers(match[12]);
      const visibleStacks = numbers(match[13]);
      const visibleContributions = numbers(match[14]);
      const foldedSeatIds = numbers(match[15]);
      const priorAllInActors = numbers(match[16]);
      const priorAllInEvents = match[17] ? match[17].split(',') : [];
      assert.ok(visibleSeatIds.length >= 2 && visibleSeatIds.length <= seats, `${file}:${index + 1}: seat map length mismatch`);
      assert.equal(visibleStacks.length, visibleSeatIds.length, `${file}:${index + 1}: stack vector length mismatch`);
      assert.equal(visibleContributions.length, visibleSeatIds.length, `${file}:${index + 1}: contribution vector length mismatch`);
      game.currentHand.decisions.push({ seat, role: role.id, policy: match[2], action: match[3], amount: +match[4],
        positionFromButton: +match[5], street: match[6], pot: +match[7], toCall: +match[8], heroStack: +match[9],
        activeOpponents: +match[10], bigBlind: +match[11], visibleSeatIds, visibleStacks, visibleContributions,
        foldedSeatIds, priorAllInActors, priorAllInEvents, legalActions: match[18] });
      continue;
    }
    match = /^STACKS (\[.*\])$/.exec(line);
    if (match && game.currentHand) {
      game.currentHand.stacks = JSON.parse(match[1]);
      continue;
    }
    match = /^COMPLETE winner=(\d+) hands=(\d+)$/.exec(line);
    if (match) {
      assert.equal(game.roles.size, seats, `${file}:${index + 1}: incomplete role roster`);
      assert.equal(game.hands.length, +match[2], `${file}:${index + 1}: hand count mismatch`);
      assert.deepEqual(game.hands.at(-1).stacks.filter(stack => stack > 0),
        [game.startingStack * seats], `${file}:${index + 1}: final stack must contain all chips`);
      game.winner = +match[1];
      assert.ok(game.roles.has(game.winner), `${file}:${index + 1}: winner has no role`);
      games.push(game);
      game = undefined;
      continue;
    }
  }
  assert.equal(game, undefined, `${file}: unfinished tournament`);
  assert.ok(games.length, `${file}: no complete tournaments`);
  return games;
}

function decisionContext(decision) {
  const heroIndex = decision.visibleSeatIds.indexOf(decision.seat);
  const callAmount = Math.min(decision.toCall, decision.heroStack);
  const heroCommitted = decision.visibleContributions[heroIndex] + callAmount;
  const folded = new Set(decision.foldedSeatIds);
  const opponentCanCover = decision.visibleSeatIds.some((seat, index) => seat !== decision.seat
    && !folded.has(seat) && decision.visibleContributions[index] + decision.visibleStacks[index] >= heroCommitted);
  const commitmentPercent = decision.heroStack ? Math.min(100, Math.ceil(callAmount * 100 / decision.heroStack)) : 0;
  const facingAllInRaise = decision.toCall > 0 && decision.priorAllInEvents.some(event => /:raise:\d+$/.test(event));
  const largePrice = decision.toCall > 0 && decision.toCall / Math.max(1, decision.pot) >= 0.75;
  return { heroIndex, callAmount, opponentCanCover, commitmentPercent,
    coveredCommitment: commitmentPercent >= 75 && opponentCanCover,
    shortStack: decision.heroStack <= decision.bigBlind * 10, facingAllInRaise, largePrice };
}

function summarize(root) {
  const games = walk(root).flatMap(file => parseTrace(file, root));
  const groups = new Map();
  for (const game of games) {
    assert.ok(game.policyOffset >= 0 && game.policyOffset < game.seats);
    assert.ok(game.buttonOffset >= 0 && game.buttonOffset < game.seats);
    assert.equal(game.firstButton, ((game.seed % game.seats) + game.seats + game.buttonOffset) % game.seats,
      `seed ${game.seed}, table ${game.seats}: button offset mismatch`);
    const groupKey = `${game.profile}|${game.seats}|${game.seed}|${game.buttonOffset}`;
    const group = groups.get(groupKey) ?? { profile: game.profile, seats: game.seats, seed: game.seed,
      buttonOffset: game.buttonOffset, variants: new Map() };
    assert.ok(!group.variants.has(game.policyOffset), `${groupKey}: duplicate policy rotation`);
    group.variants.set(game.policyOffset, game);
    groups.set(groupKey, group);
  }

  const roleSeats = new Map();
  const rolePositions = new Map();
  const roleMetrics = new Map();
  const seedMetrics = new Map();
  let tournamentCount = 0, handCount = 0, decisionCount = 0, decisionContextCount = 0;
  let allInRaiseResponses = 0, largePriceResponses = 0, coveredCommitmentCalls = 0, shortStackDecisions = 0;

  function roleStat(key) {
    if (!roleMetrics.has(key)) roleMetrics.set(key, { games: 0, wins: 0, trajectoryShares: [],
      decisions: 0, allInRaiseFacing: 0, allInRaiseFolds: 0, allInRaiseCalls: 0, allInRaiseAggressiveResponses: 0,
      largePriceFacing: 0, largePriceFolds: 0, largePriceCalls: 0, largePriceAggressiveResponses: 0,
      coveredCommitmentCalls: 0, coveredCommitmentFolds: 0, shortStackDecisions: 0, shortStackAllIns: 0 });
    return roleMetrics.get(key);
  }
  function seedStat(key) {
    if (!seedMetrics.has(key)) seedMetrics.set(key, { games: 0, wins: 0, trajectoryShares: [] });
    return seedMetrics.get(key);
  }

  for (const group of groups.values()) {
    assert.equal(group.variants.size, group.seats, `${group.profile}/${group.seats}/${group.seed}/${group.buttonOffset}: missing role rotation`);
    const ordered = [...group.variants.values()].sort((a, b) => a.policyOffset - b.policyOffset);
    const baseline = ordered[0];
    for (const game of ordered) {
      const commonHands = Math.min(game.hands.length, baseline.hands.length);
      assert.deepEqual(game.hands.slice(0, commonHands).map(hand => hand.dealSeed),
        baseline.hands.slice(0, commonHands).map(hand => hand.dealSeed),
        `${group.profile}/${group.seats}/${group.seed}/${group.buttonOffset}: deal-seed prefix changed with role assignment`);
      for (const [seat, role] of game.roles) {
        const key = `${game.profile}|${game.seats}|${role.id}`;
        roleSeats.set(key, (roleSeats.get(key) ?? new Set()).add(seat));
        const position = (seat - game.firstButton + game.seats) % game.seats;
        rolePositions.set(key, (rolePositions.get(key) ?? new Set()).add(position));
      }
      tournamentCount++;
      handCount += game.hands.length;
      const roleTrajectory = new Map([...game.roles.values()].map(role => [role.id, []]));
      for (const hand of game.hands) {
        for (const decision of hand.decisions) {
          decisionCount++;
          const key = `${game.profile}|${game.seats}|${decision.role}`;
          const stat = roleStat(key);
          stat.decisions++;
          const context = decisionContext(decision);
          decisionContextCount++;
          if (context.facingAllInRaise) {
            stat.allInRaiseFacing++;
            allInRaiseResponses++;
            if (decision.action === 'FOLD') stat.allInRaiseFolds++;
            else if (decision.action === 'CALL') stat.allInRaiseCalls++;
            else stat.allInRaiseAggressiveResponses++;
          }
          if (context.largePrice) {
            stat.largePriceFacing++;
            largePriceResponses++;
            if (decision.action === 'FOLD') stat.largePriceFolds++;
            else if (decision.action === 'CALL') stat.largePriceCalls++;
            else stat.largePriceAggressiveResponses++;
          }
          if (context.coveredCommitment && decision.toCall > 0) {
            if (decision.action === 'CALL' || decision.action === 'ALL_IN') stat.coveredCommitmentCalls++;
            if (decision.action === 'FOLD') stat.coveredCommitmentFolds++;
            coveredCommitmentCalls++;
          }
          if (context.shortStack) {
            stat.shortStackDecisions++;
            shortStackDecisions++;
            if (decision.action === 'ALL_IN') stat.shortStackAllIns++;
          }
        }
        if (hand.stacks) {
          const total = game.startingStack * game.seats;
          for (const [seat, role] of game.roles)
            roleTrajectory.get(role.id).push(hand.stacks[seat] / total);
        }
      }
      for (const [seat, role] of game.roles) {
        const key = `${game.profile}|${game.seats}|${role.id}`;
        const stat = roleStat(key);
        stat.games++;
        if (game.winner === seat) stat.wins++;
        stat.trajectoryShares.push(average(roleTrajectory.get(role.id)));
        const seedKey = `${game.profile}|${game.seats}|${game.seed}|${role.id}`;
        const seed = seedStat(seedKey);
        seed.games++;
        if (game.winner === seat) seed.wins++;
        seed.trajectoryShares.push(average(roleTrajectory.get(role.id)));
      }
    }
  }

  const rows = [...roleMetrics.entries()].map(([key, stat]) => {
    const [profile, seatsText, strategy] = key.split('|');
    const clusters = [...seedMetrics.entries()].filter(([seedKey]) => seedKey.startsWith(`${profile}|${seatsText}|`)
      && seedKey.endsWith(`|${strategy}`)).map(([seedKey, value]) => ({
        seed: +seedKey.split('|')[2],
        winRate: value.wins / value.games,
        meanHandEndStackShare: average(value.trajectoryShares),
      }));
    const seedWinRates = clusters.map(value => value.winRate);
    const seedShares = clusters.map(value => value.meanHandEndStackShare);
    return { profile, seats: +seatsText, strategy, roleVisitsEverySeat: roleSeats.get(key)?.size === +seatsText,
      roleVisitsEveryButtonPosition: rolePositions.get(key)?.size === +seatsText,
      games: stat.games, wins: stat.wins, rawWinRate: stat.wins / stat.games,
      seedClusters: clusters.length, meanWinRateAcrossSeedClusters: average(seedWinRates),
      winRateStandardErrorAcrossSeeds: standardError(seedWinRates),
      meanHandEndStackShare: average(stat.trajectoryShares),
      meanHandEndStackShareAcrossSeedClusters: average(seedShares),
      handEndStackShareStandardErrorAcrossSeeds: standardError(seedShares),
      decisions: stat.decisions, facingPriorAllInRaise: stat.allInRaiseFacing,
      foldFacingPriorAllInRaise: stat.allInRaiseFolds, callFacingPriorAllInRaise: stat.allInRaiseCalls,
      aggressiveResponseFacingPriorAllInRaise: stat.allInRaiseAggressiveResponses,
      facingLargePriceAtLeast75PercentOfPot: stat.largePriceFacing,
      foldFacingLargePrice: stat.largePriceFolds, callFacingLargePrice: stat.largePriceCalls,
      aggressiveResponseFacingLargePrice: stat.largePriceAggressiveResponses,
      coveredCommitmentCalls: stat.coveredCommitmentCalls, coveredCommitmentFolds: stat.coveredCommitmentFolds,
      shortStackDecisionsAtOrBelow10BigBlinds: stat.shortStackDecisions, shortStackAllInActions: stat.shortStackAllIns };
  }).sort((a, b) => a.profile.localeCompare(b.profile) || a.seats - b.seats || b.meanHandEndStackShareAcrossSeedClusters - a.meanHandEndStackShareAcrossSeedClusters || a.strategy.localeCompare(b.strategy));

  for (const row of rows) {
    assert.ok(row.roleVisitsEverySeat, `${row.profile}/${row.seats}/${row.strategy}: role did not visit every seat`);
    assert.ok(row.roleVisitsEveryButtonPosition, `${row.profile}/${row.seats}/${row.strategy}: role did not visit every button position`);
  }
  for (const group of groups.values()) {
    const seedCount = new Set([...group.variants.values()].map(game => game.seed)).size;
    assert.equal(seedCount, 1);
  }
  const pairedEquity = [];
  const observedProfiles = [...new Set([...groups.values()].map(group => group.profile))].sort();
  for (const profile of observedProfiles) {
    const observedSeats = [...new Set([...groups.values()].filter(group => group.profile === profile)
      .map(group => group.seats))].sort((a, b) => a - b);
    for (const seats of observedSeats) {
      const seedIds = [...new Set([...seedMetrics.keys()].map(key => key.split('|'))
        .filter(parts => parts[0] === profile && +parts[1] === seats).map(parts => +parts[2]))].sort((a, b) => a - b);
      const perSeed = seedIds.map(seed => {
        const baseline = seedMetrics.get(`${profile}|${seats}|${seed}|equity-baseline`);
        const aware = seedMetrics.get(`${profile}|${seats}|${seed}|equity-stack-aware`);
        assert.ok(baseline && aware, `${profile}/${seats}/${seed}: incomplete paired equity variants`);
        return { seed,
          winRateDeltaStackAwareMinusBaseline: aware.wins / aware.games - baseline.wins / baseline.games,
          trajectoryShareDeltaStackAwareMinusBaseline: average(aware.trajectoryShares) - average(baseline.trajectoryShares),
          baselineGames: baseline.games, stackAwareGames: aware.games };
      });
      pairedEquity.push({ profile, seats, independentSeedClusters: perSeed.length,
        meanWinRateDelta: average(perSeed.map(value => value.winRateDeltaStackAwareMinusBaseline)),
        winRateDeltaStandardError: standardError(perSeed.map(value => value.winRateDeltaStackAwareMinusBaseline)),
        meanTrajectoryShareDelta: average(perSeed.map(value => value.trajectoryShareDeltaStackAwareMinusBaseline)),
        trajectoryShareDeltaStandardError: standardError(perSeed.map(value => value.trajectoryShareDeltaStackAwareMinusBaseline)),
        perSeed });
    }
  }
  return { schemaVersion: 1, evaluation: 'offline tournament strategy and button rotation',
    methodology: { pairedBy: 'profile, seat count, tournament seed, button offset; strategy offset changes only seat-to-policy assignment',
      independentUnit: 'tournament seed cluster; button and strategy rotations are repeated measures, not independent games',
      handAssignment: 'same seed and button offset retain the same deal-seed prefix across strategy rotations',
      stackTrajectory: 'mean fraction of total chips held at each completed hand, averaged within tournament, then equally across tournaments/seeds',
      allInContext: 'prior public ALL_IN actions are distinguished as call versus price-raising action; responses count only when a call price remains',
      caveats: ['small independent seed count; results are exploratory', 'win rates are noisy and coupled by the shared table',
        'no opponent action-conditioned hand ranges, calibrated EV, rake, bounty, or ICM model',
        'covered-stack signal is a public chip-coverage proxy, not exact side-pot equity'] },
    totals: { tournaments: tournamentCount, hands: handCount, decisions: decisionCount, publicDecisionContexts: decisionContextCount,
      priorAllInRaiseResponseDecisions: allInRaiseResponses, largePriceResponseDecisions: largePriceResponses,
      coveredCommitmentDecisions: coveredCommitmentCalls, shortStackDecisions: shortStackDecisions }, rows, pairedEquity };
}

function markdown(summary) {
  const lines = ['# Rezultate exploratorii: rotația strategiilor de turneu', '',
    `Turnee: ${summary.totals.tournaments}; mâini încheiate: ${summary.totals.hands}; decizii: ${summary.totals.decisions}.`,
    `Decizii cu all-in anterior care a mărit prețul: ${summary.totals.priorAllInRaiseResponseDecisions}; contexte de preț mare: ${summary.totals.largePriceResponseDecisions}; stack scurt (≤10 BB): ${summary.totals.shortStackDecisions}.`,
    '', '## Strategii și stivă', '',
    'Stiva medie la închiderea mâinilor este media ponderii de jetoane pe mâna fiecărui turneu, apoi media egal ponderată pe jocuri. Rotațiile sunt măsurători repetate; seed-ul turneului rămâne unitatea independentă.', '',
    '| Profil | Locuri | Rol | Jocuri | Victorii | Rată victorii / seed | Stivă medie / seed | SE rată | SE stivă | Decizii | Răspunsuri la raise all-in (fold/call/agresiv) | Call de angajament acoperit | Acțiuni short-stack all-in |',
    '|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|'];
  for (const row of summary.rows) {
    lines.push(`| ${row.profile} | ${row.seats} | ${row.strategy} | ${row.games} | ${row.wins} | ${(row.meanWinRateAcrossSeedClusters * 100).toFixed(1)}% | ${(row.meanHandEndStackShareAcrossSeedClusters * 100).toFixed(1)}% | ${row.winRateStandardErrorAcrossSeeds === null ? 'n/a' : (row.winRateStandardErrorAcrossSeeds * 100).toFixed(1) + '%'} | ${row.handEndStackShareStandardErrorAcrossSeeds === null ? 'n/a' : (row.handEndStackShareStandardErrorAcrossSeeds * 100).toFixed(1) + '%'} | ${row.decisions} | ${row.facingPriorAllInRaise} (${row.foldFacingPriorAllInRaise}/${row.callFacingPriorAllInRaise}/${row.aggressiveResponseFacingPriorAllInRaise}) | ${row.coveredCommitmentCalls}/${row.coveredCommitmentFolds} | ${row.shortStackAllInActions}`);
  }
  lines.push('', '## Comparație pereche: equity cu risc de stack vs equity de bază', '',
    'Delta = stack-aware minus baseline. Fiecare seed reunește toate rotațiile de loc și buton, apoi seed-urile se mediază egal. Erorile standard sunt descriptive; numărul mic de seed-uri nu susține concluzii de superioritate.', '',
    '| Profil | Locuri | Seed-uri independente | Δ rată victorii | SE | Δ pondere medie a stivei | SE |',
    '|---|---:|---:|---:|---:|---:|---:|');
  for (const row of summary.pairedEquity) {
    lines.push(`| ${row.profile} | ${row.seats} | ${row.independentSeedClusters} | ${(row.meanWinRateDelta * 100).toFixed(1)} pp | ${row.winRateDeltaStandardError === null ? 'n/a' : (row.winRateDeltaStandardError * 100).toFixed(1) + ' pp'} | ${(row.meanTrajectoryShareDelta * 100).toFixed(2)} pp | ${row.trajectoryShareDeltaStandardError === null ? 'n/a' : (row.trajectoryShareDeltaStandardError * 100).toFixed(2) + ' pp'} |`);
  }
  lines.push('', '## Interpretare și limite', '',
    '- Rândurile ordonează descriptiv rezultatele de stack-share; nu sunt o scară de calitate și nu justifică singure schimbarea nivelului Hard.',
    '- Win-rate-ul este foarte zgomotos într-un turneu cu eliminare. Citește-l împreună cu traiectoria stack-share și acțiunile în contexte de raise all-in, pot mare și stack scurt.',
    '- „Acoperă angajamentul” este o verificare publică a stivei și contribuțiilor, nu equity exactă de side pot, ICM sau model de bounty.',
    '- Politica nu estimează încă range-uri condiționate de acțiunile fiecărui adversar și nu are EV calibrat/rake. Rezultatele rămân offline și exploratorii.',
    '- Jurnalele complete și auditurile independente sunt lângă acest raport; `run-provenance.json` identifică sursa și hash-urile YAML/codului.');
  return lines.join('\n') + '\n';
}

try {
  const root = process.argv[2];
  if (!root || process.argv.length !== 3) throw new Error('Usage: node scripts/summarize-strategy-rotation-tournaments.cjs evidence-root');
  const result = summarize(path.resolve(root));
  console.log(JSON.stringify(result, null, 2));
  fs.writeFileSync(path.join(path.resolve(root), 'strategy-summary.md'), markdown(result), 'utf8');
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
