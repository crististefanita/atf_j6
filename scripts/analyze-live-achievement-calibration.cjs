const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const header = 'difficulty,seed,seats,hand,rank,seat,terminal,dealt,bigBlind,potWon,returned,category,showdownPlayers,potRecipients,sideEligible,sideWon,splitEligible,splitWon,dealer,smallBlind,activeAtStart,boardCards,bigBlindCheckedToFlop,sideOnlyWin,sweptSidePots,boardPlaysSplit,mainPotSoleWinner,knockouts,shortStackSeen,lastStackSeen,headsUpDeficit,hardMode,manualTitle';
const ranks = ['HIGH_CARD','ONE_PAIR','TWO_PAIR','THREE_OF_A_KIND','STRAIGHT','FLUSH','FULL_HOUSE','FOUR_OF_A_KIND','STRAIGHT_FLUSH'];
const showdown = row => row.category !== '-';
const wins = row => row.potWon > 0;
const metrics = {
    POT: [r => r.dealt, wins],
    TOURNAMENT_POT: [r => r.dealt, wins],
    BIG_BLIND_POT: [r => r.dealt && r.bigBlind, r => r.bigBlind && wins(r)],
    SHOWDOWN: [r => r.dealt && r.showdownPlayers > 1, showdown],
    TOURNAMENT_FINISHED: [r => r.rankLocked, r => r.rankLocked],
    PODIUM: [r => r.rankLocked, r => r.rankLocked && r.rank <= 3],
    TITLE: [r => r.rankLocked, r => r.rankLocked && r.rank === 1],
    SIX_SEAT_TITLE: [r => r.rankLocked && r.seats === 6, r => r.rankLocked && r.seats === 6 && r.rank === 1],
    HARD_SIX_HIGH_CARD_POT: [r => r.dealt && r.seats === 6 && r.hardMode && r.showdownPlayers >= 2,
        r => r.seats === 6 && r.hardMode && r.showdownPlayers >= 2 && r.category === 'HIGH_CARD'
            && wins(r) && r.potRecipients === 1],
    FOUR_SEAT_TITLE: [r => r.rankLocked && r.seats === 4, r => r.rankLocked && r.seats === 4 && r.rank === 1],
    FIVE_SEAT_TITLE: [r => r.rankLocked && r.seats === 5, r => r.rankLocked && r.seats === 5 && r.rank === 1],
    STRAIGHT_OR_BETTER: [showdown, r => wins(r) && ranks.indexOf(r.category) >= 4],
    FULL_HOUSE_OR_BETTER: [showdown, r => wins(r) && ranks.indexOf(r.category) >= 6],
    SOLE_HIGH_CARD_POT: [showdown, r => wins(r) && r.category === 'HIGH_CARD' && r.potRecipients === 1],
    MULTIWAY_SHOWDOWN_POT: [r => showdown(r) && r.showdownPlayers >= 3,
        r => showdown(r) && r.showdownPlayers >= 3 && wins(r)],
    SIDE_POT: [r => r.sideEligible, r => r.sideWon],
    SPLIT_POT: [r => r.splitEligible, r => r.splitWon],
    BUTTON_POT: [r => r.dealt && r.dealer >= 0, r => r.dealer === r.seat && wins(r)],
    SMALL_BLIND_POT: [r => r.dealt && r.smallBlind >= 0, r => r.smallBlind === r.seat && wins(r)],
    HEADS_UP_POT: [r => r.dealt && r.activeAtStart === 2, r => r.activeAtStart === 2 && wins(r)],
    BIG_BLIND_CHECK_TO_FLOP: [r => r.dealt && r.bigBlind,
        r => r.bigBlind && r.bigBlindCheckedToFlop && r.boardCards >= 3],
    SHOWDOWN_POT: [r => r.dealt && showdown(r), r => showdown(r) && wins(r)],
    PAIR_SHOWDOWN_POT: [r => r.dealt && showdown(r), r => showdown(r) && wins(r) && r.category === 'ONE_PAIR'],
    MULTIWAY_POT: [r => r.dealt && r.activeAtStart >= 3, r => r.activeAtStart >= 3 && wins(r)],
    POSTFLOP_POT: [r => r.dealt && r.boardCards >= 3, r => r.boardCards >= 3 && wins(r)],
    TWO_PAIR_SHOWDOWN_POT: [r => r.dealt && showdown(r), r => showdown(r) && wins(r) && r.category === 'TWO_PAIR'],
    HEADS_UP_SHOWDOWN_POT: [r => r.dealt && r.activeAtStart === 2,
        r => r.activeAtStart === 2 && showdown(r) && wins(r)],
    SIDE_POT_ONLY: [r => r.dealt && r.sideEligible, r => r.sideOnlyWin],
    SWEPT_SIDE_POTS: [r => r.dealt && r.sideEligible, r => r.sweptSidePots],
    PREFLOP_FOLD_WIN: [r => r.dealt, r => wins(r) && r.boardCards === 0 && !showdown(r)],
    SOLE_MAIN_WIN_ELIMINATION: [r => r.dealt && showdown(r),
        r => showdown(r) && wins(r) && r.mainPotSoleWinner && r.knockouts > 0],
    HEADS_UP_PHASE_REACHED: [r => r.headsUpReached && r.rankLocked, r => r.headsUpReached && r.rankLocked],
    BOARD_PLAYS_SPLIT: [r => r.dealt && r.splitEligible, r => r.boardPlaysSplit],
    SHORT_STACK_TITLE: [r => r.rankLocked && r.shortStackSeen, r => r.rankLocked && r.shortStackSeen && r.rank === 1],
    TITLE_AFTER_HEADS_UP_DEFICIT: [r => r.rankLocked && r.headsUpDeficit,
        r => r.rankLocked && r.headsUpDeficit && r.rank === 1],
    TITLE_AFTER_LAST_STACK: [r => r.rankLocked && r.lastStackSeen, r => r.rankLocked && r.lastStackSeen && r.rank === 1],
    TITLE_WITHOUT_HEADS_UP: [r => r.rankLocked && r.activeAtStart >= 3,
        r => r.rankLocked && r.activeAtStart >= 3 && r.rank === 1],
};
const pending = {
    HARD_SIX_SEAT_MANUAL_TITLE: 'Auto-play simulation cannot certify a manual-only career result.',
    ALL_TABLE_SIZES_TITLE: 'This batch gives each tournament a fresh profile; it does not model a persistent career across table sizes.',
};

const median = values => {
    values.sort((a,b) => a-b);
    return values.length ? values[Math.floor((values.length-1)/2)] : null;
};

function readRun(input, batch, difficulty, sizes) {
    const data = fs.readFileSync(input);
    const [actualHeader,...lines] = data.toString('utf8').trimEnd().split(/\r?\n/);
    assert.equal(actualHeader, header, 'Wrong header in ' + input);
    const runs = new Map();
    const bool = x => { assert.ok(x === 'true' || x === 'false'); return x === 'true'; };
    for (const line of lines) {
        const cells = line.split(',');
        assert.equal(cells.length, 33);
        const [mode,seed,seats,hand,rank,seat,terminal,dealt,bigBlind,potWon,returned,category,
            showdownPlayers,potRecipients,sideEligible,sideWon,splitEligible,splitWon,dealer,smallBlind,
            activeAtStart,boardCards,bigBlindCheckedToFlop,sideOnlyWin,sweptSidePots,boardPlaysSplit,
            mainPotSoleWinner,knockouts,shortStackSeen,lastStackSeen,headsUpDeficit,hardMode,manualTitle] = cells;
        assert.equal(mode, difficulty);
        const offset = +seed - batch.firstSeed;
        assert.ok(Number.isSafeInteger(offset) && offset >= 0 && offset < batch.count);
        assert.equal(+seats, sizes[offset % sizes.length]);
        const rows = runs.get(+seed) ?? [];
        const previous = rows.at(-1);
        const row = {seed:+seed,seats:+seats,hand:+hand,rank:+rank,seat:+seat,terminal:bool(terminal),dealt:bool(dealt),
            bigBlind:bool(bigBlind),potWon:+potWon,returned:+returned,category,showdownPlayers:+showdownPlayers,
            potRecipients:+potRecipients,sideEligible:bool(sideEligible),sideWon:bool(sideWon),
            splitEligible:bool(splitEligible),splitWon:bool(splitWon),dealer:+dealer,smallBlind:+smallBlind,
            activeAtStart:+activeAtStart,boardCards:+boardCards,bigBlindCheckedToFlop:bool(bigBlindCheckedToFlop),
            sideOnlyWin:bool(sideOnlyWin),sweptSidePots:bool(sweptSidePots),boardPlaysSplit:bool(boardPlaysSplit),
            mainPotSoleWinner:bool(mainPotSoleWinner),knockouts:+knockouts,shortStackSeen:bool(shortStackSeen),
            lastStackSeen:bool(lastStackSeen),headsUpDeficit:bool(headsUpDeficit),hardMode:bool(hardMode),
            manualTitle:bool(manualTitle),rankLocked:+rank > 0 && !(previous?.rank > 0)};
        assert.equal(row.hand, rows.length + 1);
        assert.ok(!previous?.terminal && !(previous?.rank > 0 && previous.rank !== row.rank));
        assert.ok(Number.isInteger(row.rank) && row.rank >= 0 && row.rank <= row.seats
            && (!row.terminal || row.rank > 0) && (row.rank !== 1 || row.terminal));
        assert.ok([row.seat,row.dealer,row.smallBlind].every(v=>Number.isInteger(v) && v>=0 && v<row.seats),
            `Invalid seat topology in ${input} seed=${seed} hand=${hand} hero=${row.seat} button=${row.dealer} smallBlind=${row.smallBlind}`);
        assert.ok(Number.isInteger(row.activeAtStart) && row.activeAtStart>=1 && row.activeAtStart<=row.seats,
            `Invalid active player count in ${input} seed=${seed} hand=${hand} active=${row.activeAtStart} seats=${row.seats}`);
        assert.ok(Number.isInteger(row.boardCards) && [0,3,4,5].includes(row.boardCards),
            `Invalid board length in ${input} seed=${seed} hand=${hand}: ${row.boardCards}`);
        assert.ok(Number.isInteger(row.knockouts) && row.knockouts>=0,
            `Invalid knockout count in ${input} seed=${seed} hand=${hand}: ${row.knockouts}`);
        assert.ok([row.potWon,row.returned,row.showdownPlayers,row.potRecipients].every(v=>Number.isInteger(v) && v>=0),
            `Invalid settlement in ${input} seed=${seed} hand=${hand}: pot=${row.potWon} return=${row.returned} evaluated=${row.showdownPlayers} recipients=${row.potRecipients}`);
        assert.ok(category === '-' || ranks.includes(category),
            `Invalid hand category in ${input} seed=${seed} hand=${hand}: ${category}`);
        assert.ok(!showdown(row) || row.showdownPlayers >= 2,
            `Showdown without enough players in ${input} seed=${seed} hand=${hand}: category=${row.category} players=${row.showdownPlayers}`);
        assert.ok((!row.sideWon || row.sideEligible && wins(row)) && (!row.splitWon || row.splitEligible && wins(row)),
            `Invalid side/split evidence in ${input} seed=${seed} hand=${hand}: side=${row.sideEligible}/${row.sideWon}, split=${row.splitEligible}/${row.splitWon}, won=${row.potWon}`);
        assert.ok((!row.sideOnlyWin || row.sideEligible && row.sideWon && wins(row))
            && (!row.sweptSidePots || row.sideEligible && wins(row))
            && (!row.boardPlaysSplit || row.splitWon && wins(row))
            && (!row.mainPotSoleWinner || wins(row))
            && (row.hardMode === (difficulty === 'HARD'))
            && (!row.manualTitle || row.hardMode));
        rows.push(row);
        runs.set(+seed,rows);
    }
    assert.equal(runs.size,batch.count);
    for (const rows of runs.values()) {
        assert.ok(rows.at(-1).terminal || rows.length === 500, 'Partial tournament trace');
        const headsUpReached = rows.some(row => row.activeAtStart === 2);
        rows.forEach(row => { row.headsUpReached = headsUpReached; });
    }
    return {runs, sha256:crypto.createHash('sha256').update(data).digest('hex')};
}

function measure(runs, objective, horizons) {
    const [eligible, hit] = metrics[objective.metric];
    const contexts = [];
    for (const rows of runs.values()) {
        if (objective.metric === 'SIX_SEAT_TITLE' && rows[0].seats !== 6) continue;
        const opportunities = rows.filter(eligible);
        const events = rows.filter(hit);
        assert.ok(events.every(event => eligible(event)), 'Credit without opportunity: ' + objective.id);
        contexts.push({seats:rows[0].seats,seed:rows[0].seed,opportunities,events});
    }
    const windows = Object.fromEntries(horizons.map(horizon => {
        let count = 0, reached = 0;
        for (const seats of new Set(contexts.map(x => x.seats))) {
            const group = contexts.filter(x => x.seats === seats).sort((a,b)=>a.seed-b.seed);
            for (let index=0; index+horizon<=group.length; index+=horizon) {
                count++;
                const events=group.slice(index,index+horizon).reduce((n,x)=>n+x.events.length,0);
                if (events>=objective.target) reached++;
            }
        }
        return [horizon,{count,reached}];
    }));
    const sample = contexts.find(x => x.events.length > 0);
    return {
        tournaments:contexts.length,
        tournamentsWithOpportunity:contexts.filter(x=>x.opportunities.length).length,
        opportunities:contexts.reduce((total,x)=>total+x.opportunities.length,0),
        creditEvents:contexts.reduce((total,x)=>total+x.events.length,0),
        medianFirstOpportunityHand:median(contexts.flatMap(x=>x.opportunities.length?[x.opportunities[0].hand]:[])),
        medianUnlockHand:median(contexts.flatMap(x=>x.events.length>=objective.target?[x.events[objective.target-1].hand]:[])),
        example:sample ? {seed:sample.seed,seats:sample.seats,hand:sample.events[0].hand} : null,
        windows,
    };
}

const [directory, catalogFile, output, stage] = process.argv.slice(2);
assert.ok(directory && catalogFile && output, 'Usage: node scripts/analyze-live-achievement-calibration.cjs <cohorts> <catalog> <new.json> [stage]');
const plan = JSON.parse(fs.readFileSync(path.join(directory,'plan.json'),'utf8'));
const rawCatalog = fs.readFileSync(catalogFile);
const catalog = JSON.parse(rawCatalog);
const rows = Object.entries(catalog.tiers).flatMap(([tier,rules])=>rules.map(rule=>({tier,...rule})))
    .concat((catalog.retired ?? []).map(rule=>({tier:'RETIRED',...rule})));
assert.ok(rows.length > 0 && rows.every(row=>Object.hasOwn(metrics,row.metric) || Object.hasOwn(pending,row.metric)));
assert.equal(new Set(rows.map(row=>row.id)).size,rows.length);
assert.ok(!stage || plan.batches.some(batch=>batch.stage===stage), 'Unknown batch');
const seedSet = new Set();
for (const batch of plan.batches) {
    for (let n=0;n<batch.count;n++) {
        const seed=batch.firstSeed+n;
        assert.ok(!seedSet.has(seed), 'Simulation batches overlap');
        seedSet.add(seed);
    }
}
const cohorts = plan.batches.filter(batch=>!stage || batch.stage===stage).flatMap(batch=>['NORMAL','HARD'].map(difficulty=>{
    const file=batch.stage+'-'+difficulty+'.csv';
    const {runs,sha256}=readRun(path.join(directory,file),batch,difficulty,plan.seatCounts);
    const all=[...runs.values()];
    const measurements=rows.map(row=>Object.hasOwn(pending,row.metric)
        ? {id:row.id,evidence:'pending',reason:pending[row.metric]}
        : {id:row.id,evidence:'audited-normal-hard',...measure(runs,row,plan.horizons),
            bySeats:Object.fromEntries(plan.seatCounts.map(seats=>[seats,
                measure(new Map([...runs].filter(([,records])=>records[0].seats===seats)),row,plan.horizons)]))});
    return {...batch,difficulty,file,sha256,completed:all.filter(records=>records.at(-1).terminal).length,
        hands:all.reduce((total,records)=>total+records.length,0),measurements};
}));
const evidence=cohorts.filter(cohort=>cohort.stage==='holdout');
const verdicts=rows.map(row=>{
    if(row.tier==='RETIRED')return {id:row.id,verdict:'RETRAS',reason:row.reason};
    if(Object.hasOwn(pending,row.metric))return {id:row.id,verdict:'NECALIFICAT',reason:pending[row.metric]};
    if(evidence.length!==2)return {id:row.id,verdict:'CALIBRARE',reason:'Rezultatele sunt folosite pentru stabilirea pragului.'};
    const applicable = row.metric === 'HARD_SIX_HIGH_CARD_POT'
        ? evidence.filter(cohort => cohort.difficulty === 'HARD')
            .map(cohort => cohort.measurements.find(m=>m.id===row.id).bySeats['6'])
        : evidence.map(cohort=>cohort.measurements.find(m=>m.id===row.id));
    const measurements=applicable;
    const rare=measurements.some(m=>m.creditEvents<5 || m.windows[6].reached===0);
    return {id:row.id,verdict:rare?'RAR':'CONFIRMAT',reason:rare
        ? 'Frecvență redusă în cel puțin un mod la bugetul de calibrare.'
        : 'Progres observat în ambele moduri pe seed-uri noi.'};
});
fs.writeFileSync(output,JSON.stringify({schemaVersion:1,catalogSha256:crypto.createHash('sha256').update(rawCatalog).digest('hex'),
    plan,rows,cohorts,verdicts},null,2)+'\n',{flag:'wx'});
for(const c of cohorts)console.log(c.stage,c.difficulty,c.completed+'/'+c.count,'completed;',c.hands,'hands');
