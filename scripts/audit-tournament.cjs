const fs = require('node:fs');
const assert = require('node:assert/strict');

const ranks = '23456789TJQKA';
const streets = ['PREFLOP', 'FLOP', 'TURN', 'RIVER'];
const sum = values => values.reduce((a,b) => a+b, 0);
const word = value => BigInt.asUintN(64,value);
const nextSeed = seed => word(seed + 0x9e3779b97f4a7c15n);

function dealSeed(state) {
    let value = word((state ^ (state >> 30n)) * 0xbf58476d1ce4e5b9n);
    value = word((value ^ (value >> 27n)) * 0x94d049bb133111ebn);
    return BigInt.asIntN(64,value ^ (value >> 31n));
}

// Reconstruct the JDK 21 dealer stream without calling the Java motor.
function deckFor(seed) {
    const deck = [...'cdhs'].flatMap(suit => [...ranks].map(rank => rank+suit));
    for (let i=51; i>0; i--) {
        const bound = i+1;
        let index;
        while (true) {
            seed = nextSeed(seed);
            let value = word((seed ^ (seed >> 33n)) * 7109453100751455733n);
            value = word((value ^ (value >> 28n)) * -3808689974395783757n);
            const sample = Number(value >> 32n);
            if ((bound & i) === 0) { index = sample & i; break; }
            const positive = sample >>> 1;
            index = positive % bound;
            if (positive - index + i <= 0x7fffffff) break;
        }
        [deck[i],deck[index]] = [deck[index],deck[i]];
    }
    return deck;
}

function rankFive(cards) {
    const values = cards.map(card => ranks.indexOf(card[0])+2).sort((a,b) => b-a);
    const counts = new Map();
    for (const value of values) counts.set(value,(counts.get(value) ?? 0)+1);
    const groups = [...counts].sort(([a,ac],[b,bc]) => bc-ac || b-a);
    const shape = groups.map(group => group[1]).join('');
    const straight = groups.length === 5 && (values[0]-values[4] === 4 ? values[0]
        : values.join() === '14,5,4,3,2' ? 5 : 0);
    const flush = cards.every(card => card[1] === cards[0][1]);
    if (flush && straight) return [8,straight];
    if (flush) return [5,...values];
    if (straight) return [4,straight];
    return [{41:7,32:6,311:3,221:2,2111:1,11111:0}[shape],...groups.map(group => group[0])];
}

function rankSeven(cards) {
    let best = -1;
    for (let a=0; a<6; a++) for (let b=a+1; b<7; b++) {
        const key = rankFive(cards.filter((_,index) => index!==a && index!==b));
        while (key.length<6) key.push(0);
        best = Math.max(best,key.reduce((score,value) => score*15+value,0));
    }
    return best;
}

function audit(file, strict, requirePublicShoveHistory = false) {
    const stats = {tournaments:0,hands:0,showdowns:0,actions:{},sidePotHands:0,sidePots:0,returns:0,
        shortCalls:0,shortAllInIncreases:0,lockedResponses:0,cumulativeReopens:0,splitPots:0,bySize:{},examples:{}};
    let table, hand, lineNo = 0;
    const ring = after => Array.from({length:table.size},(_,i) => (after+i+1)%table.size);
    const live = () => hand.active.filter(seat => !hand.folded.has(seat));
    const able = () => live().filter(seat => hand.stack[seat]>0);
    const price = () => able().length===1 ? Math.max(...hand.streetPaid) : hand.price;
    const pending = () => {
        const actors = able();
        if (live().length<2) return undefined;
        return ring(hand.after).find(seat => actors.includes(seat) && (hand.streetPaid[seat]<price()
            || (actors.length>1 && !hand.responded.has(seat))));
    };
    const remember = key => stats.examples[key] ??= {seats:table.size,seed:table.seed,hand:hand.number,line:lineNo};
    try {
        for (const line of fs.readFileSync(file,'utf8').split(/\r?\n/)) {
            lineNo++;
            if (!line) continue;
            let m;
            if (m=/^TOURNAMENT seed=(-?\d+) seats=(\d+) (?:buttonOffset=(-?\d+) firstButton=(\d+) )?secondsPerHand=(\d+) .*startingStack=(\d+), levelSeconds=(\d+)/.exec(line)) {
                assert.ok(!table || table.complete);
                const [,seed,size,offsetRaw,buttonRaw,stepRaw,stackRaw,intervalRaw] = m;
                const seatCount = +size;
                const offset = offsetRaw === undefined ? 0 : +offsetRaw;
                const buttonAnchor = buttonRaw === undefined
                    ? Number(((BigInt(seed) % BigInt(seatCount)) + BigInt(seatCount)) % BigInt(seatCount))
                    : +buttonRaw;
                const expectedAnchor = Number((((BigInt(seed) % BigInt(seatCount)) + BigInt(seatCount))
                    + ((BigInt(offset) % BigInt(seatCount)) + BigInt(seatCount))) % BigInt(seatCount));
                assert.equal(buttonAnchor, expectedAnchor, 'Tournament button offset does not match its seed');
                const levels = [...line.matchAll(/smallBlind=(\d+), bigBlind=(\d+), minimumOpen=(\d+)/g)].map(x=>x.slice(1).map(Number));
                table = {seed,size:seatCount,step:+stepRaw,interval:+intervalRaw,levels,
                    buttonAnchor,stack:Array(seatCount).fill(+stackRaw),total:seatCount * +stackRaw,number:0,
                    strategies:new Map(),shortStackShoveOpportunities:Array(seatCount).fill(0),
                    shortStackAllInRaises:Array(seatCount).fill(0)};
                table.fixedPhysicalSeats = / dealMode=FIXED_PHYSICAL_SEAT /.test(line);
                const policyOffset = / policyOffset=(-?\d+)$/.exec(line);
                if (policyOffset) table.policyOffset = +policyOffset[1];
                table.seedState = word(BigInt(seed));
                stats.bySize[size] ??= {hands:0,tournaments:0};
                hand = undefined;
            } else if (m=/^STRATEGY seat=(\d+) id=([a-z-]+) implementation=([A-Za-z0-9_$]+)$/.exec(line)) {
                assert.ok(table && table.number===0,'Strategy roster must precede its first hand');
                const seat=+m[1];
                assert.ok(seat>=0 && seat<table.size,'Strategy seat is outside the table');
                assert.ok(!table.strategies.has(seat),'Duplicate strategy seat');
                assert.ok(![...table.strategies.values()].some(value=>value.id===m[2]),'A strategy role appears twice');
                table.strategies.set(seat,{id:m[2],implementation:m[3]});
            } else if (m=/^HAND (\d+) elapsed=(\d+) eliminated=(\d+) level=(\d+) .*smallBlind=(\d+), bigBlind=(\d+), minimumOpen=(\d+)\] button=(\d+) dealSeed=(-?\d+)$/.exec(line)) {
                assert.ok(!table.complete && (!hand || hand.complete));
                assert.ok(table.strategies.size===0 || table.strategies.size===table.size,
                    'Strategy roster must identify every seat exactly once');
                const [number,elapsed,eliminated,level,sb,bb,open,button] = m.slice(1,9).map(Number);
                assert.equal(number,++table.number);
                assert.equal(elapsed,(number-1)*table.step);
                const active = table.stack.flatMap((chips,seat)=>chips>0?[seat]:[]);
                assert.equal(eliminated,table.size-active.length);
                assert.equal(level,Math.min(table.levels.length-1,Math.floor(elapsed/table.interval)+eliminated));
                assert.deepEqual([sb,bb,open],table.levels[level]);
                const next = after => ring(after).find(seat=>active.includes(seat));
                const expectedButton = number === 1 ? table.buttonAnchor
                    : active.length === 2 ? next(next(table.big)) : next(table.button);
                assert.equal(button,expectedButton);
                const small = active.length===2 ? button : next(button);
                table.big = next(small);
                table.button = button;
                const order = ring(button).filter(seat=>active.includes(seat));
                table.seedState = nextSeed(table.seedState);
                assert.equal(BigInt(m[9]),dealSeed(table.seedState),'Deal seed differs from tournament seed');
                const deck = deckFor(BigInt(m[9]));
                const slots = table.fixedPhysicalSeats ? table.size : order.length;
                const base = slots*2;
                const hole = new Map(order.map((seat,index)=>{
                    const slot = table.fixedPhysicalSeats ? seat : index;
                    return [seat,[deck[slot],deck[slot+slots]]];
                }));
                hand = {number,active,order,hole,button,board:[1,2,3,5,7].map(offset=>deck[base+offset]),
                    blindPlan:[['SB',small,sb],['BB',table.big,bb]],blinds:0,after:table.big,open,bigBlind:bb,street:0,price:bb,full:bb,
                    responded:new Map(),stack:[...table.stack],paid:Array(table.size).fill(0),streetPaid:Array(table.size).fill(0),
                    folded:new Set(),foldedActors:[],shortPrices:[],layers:[],allInActors:[],allInEvents:[],shoveObservations:[]};
                stats.hands++;
                stats.bySize[table.size].hands++;
            } else if (line.startsWith('SEATS ')) {
                assert.deepEqual(JSON.parse(line.slice(6)),hand.active);
            } else if (m=/^(SB|BB) (\d+) (\d+)$/.exec(line)) {
                const [kind,seat,nominal] = hand.blindPlan[hand.blinds++];
                assert.deepEqual(m.slice(1),[kind,String(seat),String(Math.min(nominal,hand.stack[seat]))]);
                hand.paid[seat] = hand.streetPaid[seat] = +m[3];
                hand.stack[seat] -= +m[3];
            } else if (m=/^DECISION seat=(\d+) policy=\S+ action=(\w+) amount=(\d+) reason=/.exec(line)) {
                assert.ok(!hand.decision);
                hand.decision = m.slice(1);
                const context=/ positionFromButton=(\d+) street=(PREFLOP|FLOP|TURN|RIVER) pot=(\d+) toCall=(\d+) heroStack=(\d+) activeOpponents=(\d+) bigBlind=(\d+) visibleSeatIds=\[([0-5,]*)\] visibleStacks=\[([0-9,]*)\] visibleContributions=\[([0-9,]*)\] foldedSeatIds=\[([0-5,]*)\] priorAllInActors=\[([0-5,]*)\] priorAllInEvents=\[([0-5:callraise0-9,]*)\](?: publicOpponentTendencies=\[([0-5:0-9,]*)\])? legalActions=(.*)$/.exec(line);
                if (!context && requirePublicShoveHistory)
                    assert.fail('Decision has malformed or incomplete public context at line ' + lineNo);
                if (context) {
                    const [position,street,pot,toCall,heroStack,opponents,bigBlind] = context.slice(1,8);
                    const ids=context[8].split(',').filter(Boolean).map(Number);
                    const stacks=context[9].split(',').filter(Boolean).map(Number);
                    const contributions=context[10].split(',').filter(Boolean).map(Number);
                    const folded=context[11].split(',').filter(Boolean).map(Number);
                    const priorAllIns=context[12].split(',').filter(Boolean).map(Number);
                    const priorAllInEvents=context[13].split(',').filter(Boolean);
                    const publicTendencies=context[14];
                    const legal=context[15].split(',').map(value=>value.split(':'));
                    const seat=+m[1], expectedIds=hand.active;
                    assert.deepEqual(ids,expectedIds,'Visible seat map differs from active seats');
                    assert.deepEqual(stacks,expectedIds.map(value=>hand.stack[value]),'Visible stacks differ from public state');
                    assert.deepEqual(contributions,expectedIds.map(value=>hand.paid[value]),'Visible contributions differ from public state');
                    assert.deepEqual(folded,hand.foldedActors,'Folded seat context differs from public history');
                    assert.deepEqual(priorAllIns,hand.allInActors,'Prior all-in actions differ from public history');
                    assert.deepEqual(priorAllInEvents,hand.allInEvents,'Prior all-in call/raise context differs from public history');
                    if (requirePublicShoveHistory) assert.notEqual(publicTendencies,undefined,
                        'Decision omits public tournament-local shove history');
                    if (publicTendencies !== undefined) {
                        const observed = publicTendencies ? publicTendencies.split(',').map(value=>value.split(':').map(Number)) : [];
                        const expected = ids.map(physicalSeat=>[physicalSeat,
                            table.shortStackShoveOpportunities[physicalSeat],table.shortStackAllInRaises[physicalSeat]]);
                        assert.deepEqual(observed,expected,'Public shove history differs from prior completed-hand actions');
                    }
                    assert.equal(+position,expectedIds.indexOf(seat)-expectedIds.indexOf(hand.button) < 0
                        ? expectedIds.indexOf(seat)-expectedIds.indexOf(hand.button)+expectedIds.length
                        : expectedIds.indexOf(seat)-expectedIds.indexOf(hand.button));
                    assert.equal(street,streets[hand.street]);
                    assert.equal(+pot,sum(hand.paid));
                    assert.equal(+toCall,Math.max(0,price()-hand.streetPaid[seat]));
                    assert.equal(+heroStack,hand.stack[seat]);
                    assert.equal(+opponents,live().length-1);
                    assert.equal(+bigBlind,hand.bigBlind);
                    const [actionName, amountText] = hand.decision.slice(1);
                    const amount = +amountText;
                    const allIn = legal.find(([type])=>type==='ALL_IN');
                    const opportunity = street==='PREFLOP' && +heroStack>+bigBlind*2 && +heroStack<=+bigBlind*10
                        && allIn!==undefined && +allIn[1]>+toCall;
                    hand.shoveObservations.push({seat,opportunity,
                        raisedAllIn:opportunity && actionName==='ALL_IN' && amount>+toCall});
                    assert.ok(legal.some(([type,,minimum,maximum])=>type===m[2]
                        && +m[3]>=+minimum && +m[3]<=+maximum),'Decision is absent from its recorded legal actions');
                }
            } else if (m=/^(PREFLOP|FLOP|TURN|RIVER) (\d+) (\w+) (\d+)$/.exec(line)) {
                const [,street,seatText,action,amountText] = m;
                if (strict || hand.decision) assert.deepEqual(m.slice(2),hand.decision);
                hand.decision = undefined;
                const seat = +seatText, amount = +amountText, stack = hand.stack[seat];
                assert.equal(hand.blinds,2);
                assert.equal(street,streets[hand.street]);
                assert.equal(seat,pending(),'Invalid turn order');
                const wager = price(), due = wager-hand.streetPaid[seat], previous = hand.responded.get(seat);
                const canRaise = able().length>1 && (previous===undefined || wager-previous>=hand.full);
                if (previous!==undefined && due>0 && able().length>1) {
                    if (!canRaise) { stats.lockedResponses++; remember('locked'); }
                    if (canRaise && hand.shortPrices.filter(value=>value>previous).length>=2) {
                        stats.cumulativeReopens++; remember('cumulative');
                    }
                }
                assert.ok(amount<=stack);
                switch (action) {
                    case 'FOLD': assert.ok(due>0 && amount===0); hand.folded.add(seat); hand.foldedActors.push(seat); break;
                    case 'CHECK': assert.ok(due===0 && amount===0); break;
                    case 'CALL':
                        assert.ok(due>0 && amount===Math.min(due,stack),'Call amount differs from price or stack');
                        if (amount<due) stats.shortCalls++;
                        break;
                    case 'BET': assert.ok(wager===0 && canRaise && amount>=hand.full); break;
                    case 'RAISE': assert.ok(wager>0 && canRaise && amount-due>=hand.full); break;
                    case 'ALL_IN': assert.ok(amount===stack && (amount<=due || canRaise)); break;
                    default: assert.fail('Unknown action '+action);
                }
                const increase = amount-due;
                if (increase>0) {
                    hand.price = wager+increase;
                    if (increase>=hand.full) { hand.full=increase; hand.shortPrices=[]; }
                    else { hand.shortPrices.push(hand.price); stats.shortAllInIncreases++; remember('shortIncrease'); }
                }
                hand.responded.set(seat,Math.max(wager,hand.price));
                hand.after = seat;
                hand.paid[seat] += amount;
                hand.streetPaid[seat] += amount;
                hand.stack[seat] -= amount;
                if (action==='ALL_IN') {
                    if (!hand.allInActors.includes(seat)) hand.allInActors.push(seat);
                    hand.allInEvents.push(seat+':'+(increase>0?'raise':'call')+':'+amount);
                }
                stats.actions[action] = (stats.actions[action] ?? 0)+1;
            } else if (m=/^(FLOP|TURN|RIVER) \[(.*)\]$/.exec(line)) {
                assert.equal(pending(),undefined,'Street closed with pending action');
                assert.ok(live().length>1 && !hand.decision);
                assert.equal(m[1],streets[++hand.street]);
                assert.deepEqual(m[2].split(', '),hand.board.slice(0,hand.street+2),'Board differs from deal seed');
                hand.streetPaid.fill(0);
                hand.responded.clear();
                hand.shortPrices = [];
                hand.after = table.button;
                hand.price = 0;
                hand.full = hand.open;
            } else if (/^(POT|RETURN) /.test(line)) {
                assert.ok(!hand.awards && !hand.decision);
                hand.layers.push(line);
            } else if (m=/^(SHOWDOWN|FOLD_WIN) (\[.*\])$/.exec(line)) {
                assert.equal(hand.blinds,2);
                assert.equal(pending(),undefined);
                assert.ok(!hand.awards && !hand.decision);
                const showdown = m[1]==='SHOWDOWN', contenders = live();
                assert.ok(showdown ? contenders.length>1 && hand.street===3 : contenders.length===1);
                const scores = new Map(contenders.map(seat=>[seat,showdown?rankSeven([...hand.board,...hand.hole.get(seat)]):0]));
                const awards = Array(table.size).fill(0), expected = [];
                let prior = 0, pots = 0;
                for (const cap of [...new Set(hand.paid.filter(Boolean))].sort((a,b)=>a-b)) {
                    const contributors = hand.order.filter(seat=>hand.paid[seat]>=cap);
                    const chips = (cap-prior)*contributors.length;
                    prior = cap;
                    if (contributors.length===1) {
                        awards[contributors[0]] += chips;
                        expected.push('RETURN seat='+contributors[0]+' amount='+chips);
                        stats.returns++;
                        continue;
                    }
                    const eligible = contributors.filter(seat=>scores.has(seat));
                    assert.ok(eligible.length>0);
                    const best = Math.max(...eligible.map(seat=>scores.get(seat)));
                    const winners = eligible.filter(seat=>scores.get(seat)===best);
                    winners.forEach((seat,i)=>{ awards[seat] += Math.floor(chips/winners.length)+(i<chips%winners.length?1:0); });
                    if (winners.length>1) stats.splitPots++;
                    const list = seats => '['+seats.join(', ')+']';
                    expected.push('POT cap='+cap+' amount='+chips+' eligible='+list(eligible)+' winners='+list(winners));
                    pots++;
                }
                if (strict || hand.layers.length) assert.deepEqual(hand.layers,expected,'Pot or return events differ');
                assert.deepEqual(JSON.parse(m[2]),awards,'Settlement differs from independent evaluator');
                hand.awards = awards;
                if (showdown) stats.showdowns++;
                if (pots>1) { stats.sidePotHands++; stats.sidePots+=pots-1; remember('sidePot'); }
            } else if (line.startsWith('STACKS ')) {
                assert.ok(hand.awards && !hand.complete);
                table.stack = hand.stack.map((chips,seat)=>chips+hand.awards[seat]);
                assert.deepEqual(JSON.parse(line.slice(7)),table.stack);
                assert.equal(sum(table.stack),table.total);
                for (const decision of hand.shoveObservations) {
                    if (!decision.opportunity) continue;
                    table.shortStackShoveOpportunities[decision.seat]++;
                    if (decision.raisedAllIn) table.shortStackAllInRaises[decision.seat]++;
                }
                hand.complete = true;
            } else if (m=/^COMPLETE winner=(\d+) hands=(\d+)$/.exec(line)) {
                assert.ok(hand.complete && !table.complete);
                assert.equal(+m[2],table.number);
                assert.equal(table.stack[+m[1]],table.total);
                table.complete = true;
                stats.tournaments++;
                stats.bySize[table.size].tournaments++;
            } else assert.fail('Unknown event '+line);
        }
        assert.ok(table?.complete && stats.tournaments>0,'Incomplete journal');
    } catch (error) {
        throw new Error(file+':'+lineNo+' table='+table?.size+' seed='+table?.seed+' hand='+hand?.number+' — '+error.message,{cause:error});
    }
    return stats;
}

module.exports = {audit};
if (require.main===module) {
    try {
        const [file,...options] = process.argv.slice(2);
        const allowed = new Set(['--require-layers','--require-public-shove-history']);
        if (!file || options.some(option=>!allowed.has(option)) || new Set(options).size!==options.length)
            throw new Error('Usage: node scripts/audit-tournament.cjs trace.txt [--require-layers] [--require-public-shove-history]');
        console.log(JSON.stringify(audit(file,options.includes('--require-layers'),
            options.includes('--require-public-shove-history')),null,2));
    } catch (error) { console.error(error.message); process.exitCode=1; }
}
