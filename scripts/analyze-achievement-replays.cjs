const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const {audit} = require('./audit-tournament.cjs');

const root = path.resolve(__dirname, '..');
const read = file => JSON.parse(fs.readFileSync(path.join(root, 'config', file), 'utf8'));
const tiers = ['EASY', 'MEDIUM', 'HARD'];
const streets = ['PREFLOP', 'FLOP', 'TURN', 'RIVER'];
const isShort = (hand, seat) => hand.before[seat] <= 10 * hand.bigBlind;
const rule = (opportunity, qualifies, unit = 'hand') => ({unit, opportunity, qualifies});
const atHand = () => true;
const wins = facts => facts.won.length > 0;
const showdown = facts => facts.hand.showdown && facts.score !== undefined;
const headsUp = facts => facts.hand.active.length === 2;
const startsMultiway = facts => facts.hand.active.length >= 3;
const split = facts => facts.won.some(layer => layer.winners.length > 1);
const exactCategory = (facts, category) => showdown(facts) && Math.floor(facts.score / 15 ** 5) === category;
const mainAlone = facts => facts.hand.layers[0]?.winners.length === 1 && facts.won[0] === facts.hand.layers[0];
const allIn = facts => facts.actions.some(action => action.allIn);
const called = (facts, street) => facts.actions.some(action => (!street || action.street === street)
    && (action.kind === 'CALL' || (action.kind === 'ALL_IN' && !action.aggressive)));
const wonAtShowdown = facts => showdown(facts) && wins(facts);
const headsUpOrLater = facts => facts.player.headsUp;
const metrics = {
    SMALL_BLIND_POT: rule(f => f.hand.smallBlind === f.seat, wins),
    BUTTON_POT: rule(f => f.hand.button === f.seat, wins),
    BIG_BLIND_CHECK_TO_FLOP: rule(f => f.hand.bigBlindSeat === f.seat,
        f => f.reached >= 1 && f.actions.some(action => action.street === 'PREFLOP' && action.kind === 'CHECK')),
    HEADS_UP_POT: rule(headsUp, wins),
    PREFLOP_FOLD_WIN: rule(atHand, f => wins(f) && !f.hand.showdown && f.hand.street === 'PREFLOP'),
    SHOWDOWN_POT: rule(atHand, f => showdown(f) && wins(f)),
    FLOP_REACHED: rule(atHand, f => f.reached >= 1),
    RIVER_REACHED: rule(atHand, f => f.reached >= 3),
    PAIR_SHOWDOWN_POT: rule(atHand, f => wins(f) && exactCategory(f, 1)),
    MULTIWAY_POT: rule(startsMultiway, wins),
    POSTFLOP_POT: rule(atHand, f => wins(f) && f.reached >= 1),
    PREFLOP_CALL_TO_FLOP: rule(atHand, f => called(f, 'PREFLOP') && f.reached >= 1),
    POSTFLOP_CHECK_SHOWDOWN: rule(atHand, f => showdown(f)
        && f.actions.some(action => action.kind === 'CHECK' && action.street !== 'PREFLOP')),
    RIVER_DECISION: rule(atHand, f => f.actions.some(action => action.street === 'RIVER')),
    CALL_SHOWDOWN_POT: rule(atHand, f => called(f) && wonAtShowdown(f)),
    TWO_PAIR_SHOWDOWN_POT: rule(atHand, f => wins(f) && exactCategory(f, 2)),
    HEADS_UP_SHOWDOWN_POT: rule(headsUp, wonAtShowdown),
    SPLIT_SHOWDOWN_POT: rule(atHand, f => showdown(f) && split(f)),
    SIDE_POT_ONLY: rule(atHand, f => wins(f) && !f.won.includes(f.hand.layers[0])),
    POT_WITH_REFUND: rule(atHand, f => wins(f) && f.hand.returned[f.seat] > 0),
    SWEPT_SIDE_POTS: rule(atHand, f => showdown(f) && f.hand.layers.length > 1
        && f.hand.layers.every(layer => layer.winners.length === 1 && layer.winners[0] === f.seat)),
    SHORT_STACK_ALL_IN_POT: rule(f => isShort(f.hand, f.seat), f => allIn(f) && wins(f)),
    ALL_IN_SHOWDOWN_POT: rule(atHand, f => allIn(f) && showdown(f) && wins(f)),
    SOLE_MAIN_WIN_ELIMINATION: rule(atHand, f => showdown(f) && mainAlone(f) && f.knockouts >= 1),
    POSTFLOP_AGGRESSION_FOLD_WIN: rule(atHand, f => wins(f) && !f.hand.showdown
        && f.hand.street !== 'PREFLOP' && f.actions.some(action => action.aggressive && action.street === f.hand.street)),
    HEADS_UP_PHASE_REACHED: rule(atHand, headsUpOrLater, 'tournament'),
    BIG_BLIND_DEFENSE_SHOWDOWN_POT: rule(f => f.hand.bigBlindSeat === f.seat, f => called(f, 'PREFLOP') && wonAtShowdown(f)),
    MULTISTREET_BET_SHOWDOWN_POT: rule(atHand, f => wonAtShowdown(f) && new Set(f.actions
        .filter(action => action.aggressive && action.street !== 'PREFLOP').map(action => action.street)).size >= 2),
    RIVER_CALL_SHOWDOWN_POT: rule(atHand, f => called(f, 'RIVER') && wonAtShowdown(f)),
    RIVER_BET_SHOWDOWN_POT: rule(atHand, f => wonAtShowdown(f)
        && f.actions.some(action => action.aggressive && action.street === 'RIVER')),
    BOARD_PLAYS_SPLIT: rule(atHand, f => split(f) && f.score === f.hand.boardRank),
    ALL_IN_WHEEL_POT: rule(atHand, f => allIn(f) && wins(f) && f.score === (4 * 15 + 5) * 15 ** 4),
    HIGH_CARD_MULTIWAY_SOLE: rule(atHand, f => wins(f) && exactCategory(f, 0) && f.hand.scores.size >= 3
        && f.hand.layers.every(layer => layer.winners.length === 1 && layer.winners[0] === f.seat)),
    TWO_KNOCKOUTS_MAIN_POT: rule(atHand, f => showdown(f) && mainAlone(f) && f.knockouts >= 2),
    ODD_CHIP_SPLIT: rule(atHand, f => f.won.some(layer => layer.winners.indexOf(f.seat) < layer.chips % layer.winners.length)),
    SHORT_STACK_TITLE: rule(f => f.player.shortStack, f => f.winner === f.seat, 'tournament'),
    TITLE_AFTER_HEADS_UP_DEFICIT: rule(f => f.player.headsUpDeficit, f => f.winner === f.seat, 'tournament'),
    FIVE_SEAT_TITLE: rule(f => f.seats === 5, f => f.winner === f.seat, 'tournament'),
    TITLE_AFTER_LAST_STACK: rule(f => f.player.wasLastStack, f => f.winner === f.seat, 'tournament'),
    TITLE_WITHOUT_HEADS_UP: rule(atHand, f => f.finalActive >= 3 && f.winner === f.seat, 'tournament'),
    HEADS_UP_RIVER_CALL_SHOWDOWN_POT: rule(headsUp, f => called(f, 'RIVER') && wonAtShowdown(f)),
    THREE_STREET_CALL_SHOWDOWN_POT: rule(atHand, f => wonAtShowdown(f)
        && ['FLOP', 'TURN', 'RIVER'].every(street => called(f, street))),
};
const pending = {
    HARD_SIX_SEAT_MANUAL_TITLE: {unit: 'tournament', reason: 'Manual decision provenance and Hard game setting'},
    ALL_TABLE_SIZES_TITLE: {unit: 'table-size', reason: 'One persistent player identity across starting table sizes'},
};

function exactFields(row, fields) {
    return row && typeof row === 'object' && Object.keys(row).sort().join() === fields.split(',').sort().join();
}

function catalogRows(catalog, activeCatalog) {
    assert.ok(exactFields(catalog, 'schemaVersion,creditByUnit,groups') && catalog.schemaVersion === 1 && Array.isArray(catalog.groups),
        'Invalid expansion catalog');
    const credit = catalog.creditByUnit;
    assert.deepEqual(credit, {
        hand:{distinctBy:'hand-id', provenance:'manual-hand'},
        tournament:{distinctBy:'tournament-id', provenance:'manual-tournament'},
        'table-size':{distinctBy:'starting-seat-count', provenance:'manual-tournament'},
    }, 'Invalid expansion credit policy');
    const stableId = /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/;
    const text = value => typeof value === 'string' && value.trim().length > 0;
    const ids = new Set(Object.values(activeCatalog.tiers).flat().concat(activeCatalog.retired ?? []).map(row => row.id));
    const goals = new Set(Object.values(activeCatalog.tiers).flat().map(row => row.metric + ':' + row.target));
    const groups = new Set();
    const seenTiers = new Set();
    const rows = catalog.groups.flatMap(group => {
        assert.ok(exactFields(group, 'id,tier,titleRo,titleEn,entries') && typeof group.id === 'string' && stableId.test(group.id)
            && !groups.has(group.id) && tiers.includes(group.tier) && text(group.titleRo) && text(group.titleEn)
            && Array.isArray(group.entries) && group.entries.length > 0, 'Invalid group ' + group?.id);
        groups.add(group.id);
        seenTiers.add(group.tier);
        return group.entries.map(row => {
            const metric = row?.metric;
            const goal = metric + ':' + row?.target;
            assert.ok(exactFields(row, 'id,metric,target,titleRo,titleEn,ruleRo,ruleEn') && typeof row.id === 'string' && stableId.test(row.id)
                && !ids.has(row.id) && !goals.has(goal) && (Object.hasOwn(metrics, metric) || Object.hasOwn(pending, metric))
                && Number.isSafeInteger(row.target) && row.target >= 1 && text(row.titleRo) && text(row.titleEn)
                && text(row.ruleRo) && text(row.ruleEn), 'Invalid achievement ' + row?.id);
            const numericRule = row.target > 1 && metric !== 'ALL_TABLE_SIZES_TITLE';
            assert.equal(row.ruleRo.includes('{target}'), numericRule, 'Invalid RO target text ' + row.id);
            assert.equal(row.ruleEn.includes('{target}'), numericRule, 'Invalid EN target text ' + row.id);
            if (metric === 'ALL_TABLE_SIZES_TITLE') assert.equal(row.target, 3);
            ids.add(row.id);
            goals.add(goal);
            const unit = (metrics[metric] ?? pending[metric]).unit;
            return {tier:group.tier, group:group.id, ...row, unit, ...credit[unit]};
        });
    });
    assert.equal(seenTiers.size, tiers.length, 'Expansion must cover all difficulty tabs');
    return rows;
}

function summarize(sessions, row, horizons) {
    const careerBySeat = new Map();
    for (const player of sessions) {
        const key = player.seats + ':' + player.seat;
        if (!careerBySeat.has(key)) careerBySeat.set(key, []);
        careerBySeat.get(key).push(player);
    }
    return Object.fromEntries(horizons.map(horizon => {
        const result = {windows:0, withOpportunity:0, reached:0};
        for (const career of careerBySeat.values()) {
            career.sort((a, b) => BigInt(a.seed) < BigInt(b.seed) ? -1 : 1);
            for (let start = 0; start + horizon <= career.length; start += horizon) {
                const window = career.slice(start, start + horizon);
                result.windows++;
                if (window.some(player => player.opportunities.has(row.id))) result.withOpportunity++;
                if (window.reduce((total, player) => total + (player.counts.get(row.id) ?? 0), 0) >= row.target) result.reached++;
            }
        }
        return [horizon, result];
    }));
}

function collect(file, rows) {
    const sessions = [], tournaments = new Set(), examples = new Map();
    const measured = rows.filter(row => Object.hasOwn(metrics, row.metric));
    const handRows = measured.filter(row => metrics[row.metric].unit === 'hand');
    const tournamentRows = measured.filter(row => metrics[row.metric].unit === 'tournament');
    let players;
    function evaluate(activeRows, facts, player) {
        for (const row of activeRows) {
            const metric = metrics[row.metric];
            if (!metric.opportunity(facts)) continue;
            player.opportunities.add(row.id);
            if (!metric.qualifies(facts)) continue;
            const count = (player.counts.get(row.id) ?? 0) + 1;
            player.counts.set(row.id, count);
            if (count === row.target && facts.hand) player.thresholdHands.set(row.id, facts.hand.number);
            if (!examples.has(row.id)) examples.set(row.id, {seed:player.seed, seats:player.seats, seat:player.seat,
                ...(facts.hand && {hand:facts.hand.number})});
        }
    }
    const stats = audit(file, true, false, {
        hand(hand) {
            if (hand.number === 1) {
                const key = hand.seats + ':' + hand.seed;
                assert.ok(!tournaments.has(key), 'Repeated tournament ' + key);
                tournaments.add(key);
                players = hand.before.map((_, seat) => ({seed:hand.seed, seats:hand.seats, seat,
                    opportunities:new Set(), counts:new Map(), thresholdHands:new Map(), headsUp:false, headsUpDeficit:false, shortStack:false, wasLastStack:false}));
            }
            const knockouts = hand.active.filter(seat => hand.after[seat] === 0).length;
            for (const seat of hand.active) {
                const player = players[seat];
                const actions = hand.actions.filter(action => action.seat === seat);
                const folded = actions.find(action => action.kind === 'FOLD');
                const reached = streets.indexOf(folded?.street ?? hand.street);
                const won = hand.layers.filter(layer => layer.winners.includes(seat));
                if (hand.active.length === 2) {
                    player.headsUp = true;
                    if (3 * hand.before[seat] <= hand.before.reduce((sum, stack) => sum + stack, 0))
                        player.headsUpDeficit = true;
                } else {
                    if (isShort(hand, seat)) player.shortStack = true;
                    if (hand.active.every(other => other === seat || hand.before[other] > hand.before[seat]))
                        player.wasLastStack = true;
                }
                evaluate(handRows, {hand, seat, actions, reached, won, score:hand.showdown ? hand.scores.get(seat) : undefined, knockouts}, player);
            }
        },
        tournament(result) {
            for (const player of players) {
                evaluate(tournamentRows, {...result, seat:player.seat, player}, player);
                sessions.push(player);
            }
        },
    });
    return {stats, sessions, examples, tournaments};
}

function analyze(plan, rows, traceRoot, holdoutRoot) {
    assert.ok(exactFields(plan, 'schemaVersion,horizons,seatCounts,cohorts,holdout,screening') && plan.schemaVersion === 1
        && Array.isArray(plan.horizons) && plan.horizons.length
        && plan.horizons.every((n,i) => Number.isSafeInteger(n) && n > (plan.horizons[i - 1] ?? 0))
        && Array.isArray(plan.seatCounts) && plan.seatCounts.join() === '4,5,6'
        && Array.isArray(plan.cohorts) && plan.cohorts.length > 0, 'Invalid replay plan');
    const {holdout, screening} = plan;
    assert.ok(exactFields(holdout, 'firstSeed,tournamentsPerSeatCount,style')
        && Number.isSafeInteger(holdout.firstSeed) && Number.isSafeInteger(holdout.tournamentsPerSeatCount)
        && holdout.tournamentsPerSeatCount > 0 && Number.isSafeInteger(holdout.firstSeed + holdout.tournamentsPerSeatCount)
        && /^[a-z-]+$/.test(holdout.style), 'Invalid holdout specification');
    assert.ok(exactFields(screening, 'minimumSeedsWithEvents,easyWindow,easyReachShare')
        && Number.isSafeInteger(screening.minimumSeedsWithEvents) && screening.minimumSeedsWithEvents > 0
        && plan.horizons.includes(screening.easyWindow) && typeof screening.easyReachShare === 'number' && screening.easyReachShare > 0 && screening.easyReachShare <= 1,
        'Invalid screening specification');
    const ids = new Set(), files = new Set();
    for (const cohort of plan.cohorts) {
        assert.ok(exactFields(cohort, 'id,profile,role,file') && /^[a-z-]+$/.test(cohort.id)
            && !ids.has(cohort.id) && /^[a-z-]+$/.test(cohort.profile)
            && ['control', 'candidate'].includes(cohort.role) && /^[a-z0-9-]+\.trace$/.test(cohort.file) && !files.has(cohort.file),
            'Invalid cohort ' + cohort?.id);
        ids.add(cohort.id);
        files.add(cohort.file);
    }
    const stages = [['calibration', traceRoot]];
    if (holdoutRoot) stages.push(['holdout', holdoutRoot]);
    const profiles = new Map(), calibrationSeeds = new Set(), holdoutSeeds = new Set();
    const cohorts = stages.flatMap(([stage, directory]) => plan.cohorts.map(cohort => {
        const relative = stage === 'calibration' ? cohort.file : path.join(cohort.profile + '-' + holdout.style, cohort.role + '.trace');
        const file = path.join(directory, relative);
        const {stats, sessions, examples, tournaments} = collect(file, rows);
        const profile = stage + ':' + cohort.profile;
        if (!profiles.has(profile)) profiles.set(profile, new Map());
        const roles = profiles.get(profile);
        const stageSeeds = stage === 'calibration' ? calibrationSeeds : holdoutSeeds;
        for (const entry of tournaments) stageSeeds.add(entry.split(':')[1]);
        if (stage === 'holdout') {
            const expected = new Set(plan.seatCounts.flatMap(size =>
                Array.from({length:holdout.tournamentsPerSeatCount}, (_, index) => size + ':' + (holdout.firstSeed + index))));
            assert.deepEqual(tournaments, expected, 'Unexpected holdout seed range');
        }
        assert.ok(!roles.has(cohort.role), 'Duplicate cohort role ' + cohort.role);
        assert.deepEqual(Object.keys(stats.bySize).map(Number), plan.seatCounts, 'Wrong table-size coverage');
        roles.set(cohort.role, tournaments);
        const measures = rows.map(row => {
            if (Object.hasOwn(pending, row.metric)) return {id:row.id, evidence:'pending', ...pending[row.metric]};
            const hits = sessions.filter(player => (player.counts.get(row.id) ?? 0) > 0);
            const thresholdHands = hits.flatMap(player => player.thresholdHands.has(row.id) ? [player.thresholdHands.get(row.id)] : [])
                .sort((a, b) => a - b);
            return {id:row.id, evidence:'audited-replay', unit:metrics[row.metric].unit,
                seedsWithEvents:new Set(hits.map(player => player.seed)).size,
                medianHandAmongThresholdHits:thresholdHands[Math.floor((thresholdHands.length - 1) / 2)] ?? null,
                matchedEvents:sessions.reduce((total, player) => total + (player.counts.get(row.id) ?? 0), 0),
                windows:summarize(sessions, row, plan.horizons),
                bySeatCount:Object.fromEntries(plan.seatCounts.map(size =>
                    [size, summarize(sessions.filter(player => player.seats === size), row, plan.horizons)])),
                example:examples.get(row.id) ?? null};
        });
        return {...cohort, stage, file:relative, sha256:crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex'),
            tournaments:stats.tournaments, hands:stats.hands, measures};
    }));
    for (const [profile, roles] of profiles) {
        assert.equal(roles.size, 2, 'Missing paired cohort for ' + profile);
        assert.deepEqual(roles.get('control'), roles.get('candidate'), 'Tournament seeds differ for ' + profile);
    }
    assert.ok([...holdoutSeeds].every(seed => !calibrationSeeds.has(seed)), 'Calibration and holdout seeds overlap');
    const evidence = cohorts.filter(cohort => cohort.stage === (holdoutRoot ? 'holdout' : 'calibration'));
    const review = rows.map((row, index) => {
        if (Object.hasOwn(pending, row.metric)) return {id:row.id, status:'missing-facts', ...pending[row.metric]};
        const measures = evidence.map(cohort => cohort.measures[index]);
        const minimumSeedsWithEvents = Math.min(...measures.map(item => item.seedsWithEvents));
        const easyShares = measures.map(item => item.windows[screening.easyWindow])
            .filter(window => window.windows > 0).map(window => window.reached / window.windows);
        const minimumEarlyReach = easyShares.length ? Math.min(...easyShares) : null;
        let status = 'observed';
        if (minimumSeedsWithEvents < screening.minimumSeedsWithEvents) status = 'rare-in-cohort';
        else if (row.tier === 'EASY' && (minimumEarlyReach === null || minimumEarlyReach < screening.easyReachShare)) status = 'review-easy-tier';
        return {id:row.id, status, minimumSeedsWithEvents, minimumEarlyReach};
    });
    return {schemaVersion:1, reviewStage:holdoutRoot ? 'holdout' : 'calibration', catalog:rows, cohorts, review};
}

module.exports = {catalogRows, analyze, metrics};

if (require.main === module) {
    const [traceRoot, output, holdoutRoot] = process.argv.slice(2);
    assert.ok(traceRoot && output, 'Usage: node scripts/analyze-achievement-replays.cjs <trace directory> <new report.json> [holdout directory]');
    const rows = catalogRows(read('j6-achievement-expansion.yaml'), read('j6-achievements.yaml'));
    const report = analyze(read('j6-achievement-replay.yaml'), rows, traceRoot, holdoutRoot);
    fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n', {flag:'wx'});
    for (const cohort of report.cohorts) console.log(cohort.stage, cohort.id, cohort.tournaments, 'tournaments,', cohort.hands, 'hands');
}
