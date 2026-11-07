const fs = require('node:fs');
const assert = require('node:assert/strict');

try {
    const [candidatePath, controlPath] = process.argv.slice(2);
    assert.ok(candidatePath && controlPath, 'usage: compare-cash-sessions.cjs candidate.json control.json');
    const candidate = JSON.parse(fs.readFileSync(candidatePath, 'utf8'));
    const control = JSON.parse(fs.readFileSync(controlPath, 'utf8'));
    for (const key of ['seed', 'seats', 'startingStack', 'smallBlind', 'bigBlind', 'minimumOpen', 'buttonOffset', 'firstButton', 'handLimit'])
        assert.equal(candidate[key], control[key], `paired session mismatch: ${key}`);
    const commonHands = Math.min(candidate.hands, control.hands);
    assert.deepEqual(candidate.dealSeeds.slice(0, commonHands), control.dealSeeds.slice(0, commonHands),
        'paired sessions must start with identical deals');
    assert.equal(candidate.buttons[0], control.buttons[0], 'paired sessions must use the same initial button');
    const candidateSeats = Array.from({length: candidate.seats}, (_, seat) => seat)
        .filter(seat => ((seat + Number(BigInt(candidate.seed) % 4n) + 4) % 4) === 3);
    const candidateStack = candidateSeats.reduce((sum, seat) => sum + candidate.finalStacks[seat], 0);
    const controlStack = candidateSeats.reduce((sum, seat) => sum + control.finalStacks[seat], 0);
    const total = candidate.seats * candidate.startingStack;
    let commonButtonPrefix = 0;
    while (commonButtonPrefix < commonHands
            && candidate.buttons[commonButtonPrefix] === control.buttons[commonButtonPrefix]) commonButtonPrefix++;
    const result = {seed: candidate.seed, seats: candidate.seats, commonHands, commonButtonPrefix,
        candidateHands: candidate.hands,
        controlHands: control.hands, candidateSeats, candidateStackShare: candidateStack / total,
        controlSlotStackShare: controlStack / total, pairedStackShareDelta: (candidateStack - controlStack) / total,
        candidateSlotBusted: candidateSeats.filter(seat => candidate.finalStacks[seat] === 0).length,
        controlSlotBusted: candidateSeats.filter(seat => control.finalStacks[seat] === 0).length};
    console.log(JSON.stringify(result, null, 2));
} catch (error) {
    console.error(error.stack || String(error));
    process.exitCode = 1;
}
