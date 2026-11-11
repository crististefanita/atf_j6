const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const plan = readJson(path.join(root, 'config/j6-achievement-calibration.yaml'));
const catalog = readJson(path.join(root, 'config/j6-achievements.yaml'));
const ranks = 'HIGH_CARD ONE_PAIR TWO_PAIR THREE_OF_A_KIND STRAIGHT FLUSH FULL_HOUSE FOUR_OF_A_KIND STRAIGHT_FLUSH'.split(' ');
const header = 'mode,seed,seats,viewer,policy,hand,rank,potWon,refund,category,dealt,showdownPlayers,potRecipients,bigBlind,stack,contribution';
const [output, stage] = process.argv.slice(2);
if (!output || (stage && !['calibration', 'holdout'].includes(stage)))
  throw new Error('Usage: node scripts/analyze-achievement-calibration.cjs <run directory> [calibration|holdout]');

const legacy = {
  firstFoldWin: ['UNCONTESTED_POT', 1],
  fiveMultiwayFoldWins: ['MULTIWAY_TOURNAMENT_UNCONTESTED', 5],
  oneFullHouse: ['FULL_HOUSE_OR_BETTER', 1],
  oneStraight: ['STRAIGHT_OR_BETTER', 1],
};

function facts(hand, lockedRank) {
  const wins = hand.potWon > 0;
  const strength = ranks.indexOf(hand.category);
  const reachedShowdown = strength >= 0;
  const showdownWin = wins && reachedShowdown;
  return {
    POT: wins,
    TOURNAMENT_POT: wins,
    BIG_BLIND_POT: wins && hand.bigBlind === 1,
    SHOWDOWN: reachedShowdown,
    TOURNAMENT_FINISHED: lockedRank > 0,
    PODIUM: lockedRank >= 1 && lockedRank <= 3,
    TITLE: lockedRank === 1,
    SIX_SEAT_TITLE: lockedRank === 1 && hand.seats === 6,
    STRAIGHT_OR_BETTER: showdownWin && strength >= ranks.indexOf('STRAIGHT'),
    FULL_HOUSE_OR_BETTER: showdownWin && strength >= ranks.indexOf('FULL_HOUSE'),
    SOLE_HIGH_CARD_POT: showdownWin && strength === 0 && hand.potRecipients === 1,
    MULTIWAY_SHOWDOWN_POT: showdownWin && hand.showdownPlayers >= 3,
    UNCONTESTED_POT: wins && !reachedShowdown,
    MULTIWAY_TOURNAMENT_UNCONTESTED: wins && !reachedShowdown && hand.dealt >= 3,
  };
}

function readRun(run) {
  const file = path.join(output, run.mode + '-' + run.firstSeed + '.csv');
  const [actualHeader, ...lines] = fs.readFileSync(file, 'utf8').trim().split(/\r?\n/);
  if (actualHeader !== header) throw new Error('Unexpected CSV header in ' + file);
  const tournaments = new Map();
  for (const line of lines) {
    const columns = line.split(',');
    if (columns.length !== 16) throw new Error('Bad CSV row in ' + file);
    const [mode, seed, seats, viewer, policy, hand, rank, potWon, refund, category,
      dealt, showdownPlayers, potRecipients, bigBlind, stack, contribution] = columns;
    const offset = Number(seed) - run.firstSeed;
    if (mode !== run.mode || offset < 0 || offset >= run.count ||
        Number(seats) !== plan.seatCounts[offset % plan.seatCounts.length])
      throw new Error('Unexpected cohort member: ' + line);
    const viewers = tournaments.get(+seed) || new Map();
    const career = viewers.get(+viewer) || [];
    if (+hand !== career.length + 1 || (career.length && career[0].policy !== policy))
      throw new Error('Noncontiguous hand or changed policy: ' + line);
    career.push({seats:+seats, viewer:+viewer, policy, hand:+hand, rank:+rank, potWon:+potWon, refund:+refund,
      category, dealt:+dealt, showdownPlayers:+showdownPlayers, potRecipients:+potRecipients,
      bigBlind:+bigBlind, stack:+stack, contribution:+contribution});
    viewers.set(+viewer, career);
    tournaments.set(+seed, viewers);
  }
  if (tournaments.size !== run.count) throw new Error('Incomplete cohort: ' + file);

  let completed = 0;
  let hands = 0;
  const sessions = [];
  for (const [seed, viewers] of tournaments) {
    const all = [...viewers.values()];
    const first = all[0];
    const last = first.at(-1);
    const complete = last.rank > 0;
    if (viewers.size !== (run.mode === 'product' ? 1 : last.seats) ||
        all.some(rows => rows.length !== first.length || (rows.at(-1).rank > 0) !== complete) ||
        (!complete && first.length !== 500))
      throw new Error('Incomplete tournament trace: ' + seed);
    if (complete && run.mode === 'mixed' && all.filter(rows => rows.at(-1).rank === 1).length !== 1)
      throw new Error('Wrong number of champions: ' + seed);
    if (complete) completed++;
    hands += first.length;
    for (const rows of all) sessions.push(project(seed, rows));
  }
  return {completed, hands, sessions};
}

function project(seed, rows) {
  const events = new Map();
  let rankKnown = false;
  for (let i = 0; i < rows.length; i++) {
    const hand = rows[i];
    const justBusted = hand.stack === 0 && (i === 0 || rows[i - 1].stack > 0);
    let lockedRank = 0;
    if (!rankKnown && justBusted && rows[i + 1]) lockedRank = rows[i + 1].dealt + 1;
    else if (!rankKnown && hand.rank > 0) lockedRank = hand.rank;
    if (lockedRank > 0) {
      if (rows.at(-1).rank && rows.at(-1).rank !== lockedRank)
        throw new Error('Rank locked at elimination differs from final rank: ' + seed);
      rankKnown = true;
    }
    for (const [metric, qualifies] of Object.entries(facts(hand, lockedRank))) {
      if (!qualifies) continue;
      const previous = events.get(metric) || [];
      previous.push(hand.hand);
      events.set(metric, previous);
    }
  }
  return {seed, seats: rows[0].seats, viewer: rows[0].viewer, policy: rows[0].policy, events};
}

function summarize(sessions, metric, target) {
  const eligible = metric === 'SIX_SEAT_TITLE' ? sessions.filter(player => player.seats === 6) : sessions;
  const single = eligible.map(player => player.events.get(metric)?.[target - 1]).filter(Number.isInteger);
  single.sort((a, b) => a - b);
  const careers = new Map();
  for (const player of eligible) {
    const key = [player.seats, player.viewer, player.policy].join('/');
    const career = careers.get(key) || [];
    career.push(player);
    careers.set(key, career);
  }
  const windows = {};
  for (const horizon of [3, 6, 12]) {
    let eligibleWindows = 0;
    let unlocked = 0;
    for (const career of careers.values()) {
      career.sort((a, b) => a.seed - b.seed);
      for (let i = 0; i + horizon <= career.length; i += horizon) {
        const total = career.slice(i, i + horizon).reduce((n, s) => n + (s.events.get(metric)?.length || 0), 0);
        eligibleWindows++;
        if (total >= target) unlocked++;
      }
    }
    windows[horizon] = {eligible: eligibleWindows, achieved: unlocked};
  }
  return {eligible: eligible.length, achieved: single.length,
    medianHandAmongAchievers: single.length ? single[Math.floor((single.length - 1) / 2)] : null, windows};
}

const seedSet = new Set();
for (const run of plan.runs) {
  for (let i = 0; i < run.count; i++) {
    const seed = run.firstSeed + i;
    if (seedSet.has(seed)) throw new Error('Cohort seed overlap: ' + seed);
    seedSet.add(seed);
  }
}
const result = plan.runs.filter(run => !stage || run.stage === stage).map(run => {
  const {completed, hands, sessions} = readRun(run);
  const measures = Object.entries(catalog.tiers).flatMap(([tier, rules]) => rules.map(rule => {
    if (['SIDE_POT', 'SPLIT_POT'].includes(rule.metric)) {
      return {tier, ...rule, evidence:'unavailable', reason:'Legacy CSV omits the awarded pot layers.'};
    }
    return {
      tier, ...rule, ...summarize(sessions, rule.metric, rule.target),
      bySeatCount: Object.fromEntries(plan.seatCounts.filter(seats => rule.metric !== 'SIX_SEAT_TITLE' || seats === 6)
        .map(seats => [seats, summarize(sessions.filter(s => s.seats === seats), rule.metric, rule.target)])),
      byPolicy: Object.fromEntries([...new Set(sessions.map(s => s.policy))].sort()
        .map(policy => [policy, summarize(sessions.filter(s => s.policy === policy), rule.metric, rule.target)])),
    };
  }));
  return {...run, completed, censored: run.count - completed, hands, playerSessions: sessions.length, measures,
    previousRules: Object.fromEntries(Object.entries(legacy).map(([id, [metric, target]]) =>
      [id, summarize(sessions, metric, target)]))};
});

fs.writeFileSync(path.join(output, stage ? 'summary-' + stage + '.json' : 'summary.json'), JSON.stringify({schemaVersion: 1, runs: result}, null, 2) + '\n');
for (const run of result) console.log(run.stage, run.mode, run.completed + '/' + run.count, 'finished,', run.hands, 'hands');
