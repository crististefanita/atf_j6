const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const header = 'difficulty,seed,seats,hand,rank,terminal,dealt,bigBlind,potWon,returned,category,showdownPlayers,potRecipients,sideEligible,sideWon,splitEligible,splitWon';
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
    STRAIGHT_OR_BETTER: [showdown, r => wins(r) && ranks.indexOf(r.category) >= 4],
    FULL_HOUSE_OR_BETTER: [showdown, r => wins(r) && ranks.indexOf(r.category) >= 6],
    SOLE_HIGH_CARD_POT: [showdown, r => wins(r) && r.category === 'HIGH_CARD' && r.potRecipients === 1],
    MULTIWAY_SHOWDOWN_POT: [r => showdown(r) && r.showdownPlayers >= 3,
        r => showdown(r) && r.showdownPlayers >= 3 && wins(r)],
    SIDE_POT: [r => r.sideEligible, r => r.sideWon],
    SPLIT_POT: [r => r.splitEligible, r => r.splitWon],
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
        assert.equal(cells.length, 17);
        const [mode,seed,seats,hand,rank,terminal,dealt,bigBlind,potWon,returned,category,
            showdownPlayers,potRecipients,sideEligible,sideWon,splitEligible,splitWon] = cells;
        assert.equal(mode, difficulty);
        const offset = +seed - batch.firstSeed;
        assert.ok(Number.isSafeInteger(offset) && offset >= 0 && offset < batch.count);
        assert.equal(+seats, sizes[offset % sizes.length]);
        const rows = runs.get(+seed) ?? [];
        const previous = rows.at(-1);
        const row = {seed:+seed,seats:+seats,hand:+hand,rank:+rank,terminal:bool(terminal),dealt:bool(dealt),
            bigBlind:bool(bigBlind),potWon:+potWon,returned:+returned,category,showdownPlayers:+showdownPlayers,
            potRecipients:+potRecipients,sideEligible:bool(sideEligible),sideWon:bool(sideWon),
            splitEligible:bool(splitEligible),splitWon:bool(splitWon),rankLocked:+rank > 0 && !(previous?.rank > 0)};
        assert.equal(row.hand, rows.length + 1);
        assert.ok(!previous?.terminal && !(previous?.rank > 0 && previous.rank !== row.rank));
        assert.ok(Number.isInteger(row.rank) && row.rank >= 0 && row.rank <= row.seats
            && (!row.terminal || row.rank > 0) && (row.rank !== 1 || row.terminal));
        assert.ok([row.potWon,row.returned,row.showdownPlayers,row.potRecipients].every(v=>Number.isInteger(v) && v>=0)
            && (category === '-' || ranks.includes(category))
            && (!showdown(row) || row.showdownPlayers >= 2)
            && (!row.sideWon || row.sideEligible && wins(row)) && (!row.splitWon || row.splitEligible && wins(row)));
        rows.push(row);
        runs.set(+seed,rows);
    }
    assert.equal(runs.size,batch.count);
    for (const rows of runs.values()) assert.ok(rows.at(-1).terminal || rows.length === 500, 'Partial tournament trace');
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
assert.ok(rows.length > 0 && rows.every(row=>Object.hasOwn(metrics,row.metric)));
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
    const measurements=rows.map(row=>({id:row.id,...measure(runs,row,plan.horizons),
        bySeats:Object.fromEntries(plan.seatCounts.map(seats=>[seats,
            measure(new Map([...runs].filter(([,records])=>records[0].seats===seats)),row,plan.horizons)]))}));
    return {...batch,difficulty,file,sha256,completed:all.filter(records=>records.at(-1).terminal).length,
        hands:all.reduce((total,records)=>total+records.length,0),measurements};
}));
const evidence=cohorts.filter(cohort=>cohort.stage==='holdout');
const verdicts=rows.map(row=>{
    if(row.tier==='RETIRED')return {id:row.id,verdict:'RETRAS',reason:row.reason};
    if(evidence.length!==2)return {id:row.id,verdict:'CALIBRARE',reason:'Rezultatele sunt folosite pentru stabilirea pragului.'};
    const measurements=evidence.map(cohort=>cohort.measurements.find(m=>m.id===row.id));
    const rare=measurements.some(m=>m.creditEvents<5 || m.windows[6].reached===0);
    return {id:row.id,verdict:rare?'RAR':'CONFIRMAT',reason:rare
        ? 'Frecvență redusă în cel puțin un mod la bugetul de calibrare.'
        : 'Progres observat în ambele moduri pe seed-uri noi.'};
});
fs.writeFileSync(output,JSON.stringify({schemaVersion:1,catalogSha256:crypto.createHash('sha256').update(rawCatalog).digest('hex'),
    plan,rows,cohorts,verdicts},null,2)+'\n',{flag:'wx'});
for(const c of cohorts)console.log(c.stage,c.difficulty,c.completed+'/'+c.count,'completed;',c.hands,'hands');
