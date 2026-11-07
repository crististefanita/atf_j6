const fs = require('node:fs');
const assert = require('node:assert/strict');

function audit(file) {
    const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/).filter(Boolean);
    const sessionLines = lines.filter(line => line.startsWith('CASH_SESSION '));
    assert.equal(sessionLines.length, 1, 'one cash session header is required');
    const header = /seed=(-?\d+) seats=(\d+) startingStack=(\d+) fixedBlinds=BlindLevel\[smallBlind=(\d+), bigBlind=(\d+), minimumOpen=(\d+)\] buttonOffset=(-?\d+) firstButton=(\d+) handLimit=(\d+)/.exec(sessionLines[0]);
    assert.ok(header, `malformed cash session header: ${sessionLines[0]}`);
    const [, seedText, seatsText, stackText, sbText, bbText, openText, offsetText, firstButtonText, limitText] = header;
    const seed = BigInt(seedText);
    const seats = Number(seatsText), startingStack = Number(stackText);
    const smallBlind = Number(sbText), bigBlind = Number(bbText), minimumOpen = Number(openText);
    const buttonOffset = BigInt(offsetText), firstButton = Number(firstButtonText), handLimit = Number(limitText);
    assert.ok(seats >= 2 && seats <= 6 && startingStack > 0 && seats * startingStack <= 2_147_483_647);
    assert.ok(smallBlind > 0 && smallBlind < bigBlind && minimumOpen >= bigBlind);
    const mod = (value, base) => Number(((value % BigInt(base)) + BigInt(base)) % BigInt(base));
    const expectedFirst = mod(seed + buttonOffset, seats);
    assert.equal(firstButton, expectedFirst, 'seed and offset determine the first dealer');
    assert.ok(!lines.some(line => /TOURNAMENT|TOURNAMENT_COMPLETE|levelIndex=/.test(line)),
        'cash traces must not contain tournament levels or completion markers');

    const handLines = lines.filter(line => line.startsWith('CASH_HAND '));
    const stackLines = lines.filter(line => line.startsWith('CASH_STACKS '));
    assert.equal(handLines.length, stackLines.length);
    assert.ok(handLines.length > 0 && handLines.length <= handLimit);
    let stacks = Array(seats).fill(startingStack);
    let previousButton = firstButton;
    const buttons = [], dealSeeds = [];
    const nextFunded = after => {
        for (let distance = 1; distance <= seats; distance++) {
            const candidate = (after + distance) % seats;
            if (stacks[candidate] > 0) return candidate;
        }
        assert.fail('cash trace has no funded seat before a hand');
    };
    for (let i = 0; i < handLines.length; i++) {
        const hand = /^CASH_HAND (\d+) button=(\d+) fixedBlinds=(BlindLevel\[smallBlind=\d+, bigBlind=\d+, minimumOpen=\d+\]) dealSeed=(-?\d+)$/.exec(handLines[i]);
        assert.ok(hand, `malformed hand header at index ${i}`);
        assert.equal(Number(hand[1]), i + 1, 'hand number must be contiguous');
        const expectedButton = i === 0 ? firstButton : nextFunded(previousButton);
        assert.equal(Number(hand[2]), expectedButton, 'button advances to the next funded seat');
        assert.equal(hand[3], `BlindLevel[smallBlind=${smallBlind}, bigBlind=${bigBlind}, minimumOpen=${minimumOpen}]`,
            'blind configuration must remain fixed for every hand');
        const dealSeed = BigInt(hand[4]);
        assert.ok(!dealSeeds.includes(dealSeed), 'deal seeds must not repeat within a session');
        dealSeeds.push(dealSeed);
        buttons.push(Number(hand[2]));
        previousButton = Number(hand[2]);

        const state = /^CASH_STACKS hand=(\d+) stacks=\[([\d, ]+)\] funded=(\d+)$/.exec(stackLines[i]);
        assert.ok(state, `malformed stack snapshot at index ${i}`);
        assert.equal(Number(state[1]), i + 1);
        const after = state[2].split(',').map(value => Number(value.trim()));
        assert.equal(after.length, seats);
        assert.ok(after.every(value => Number.isInteger(value) && value >= 0));
        assert.equal(after.reduce((sum, value) => sum + value, 0), seats * startingStack,
            'cash session must conserve the full chip supply');
        assert.equal(Number(state[3]), after.filter(value => value > 0).length);
        stacks = after;
    }
    const complete = lines.filter(line => line.startsWith('CASH_SESSION_COMPLETE '));
    assert.equal(complete.length, 1);
    const final = /^CASH_SESSION_COMPLETE hands=(\d+) stacks=\[([\d, ]+)\] buttons=\[([\d, ]+)\]$/.exec(complete[0]);
    assert.ok(final, 'malformed cash completion line');
    assert.equal(Number(final[1]), handLines.length);
    assert.deepEqual(final[2].split(',').map(value => Number(value.trim())), stacks);
    assert.deepEqual(final[3].split(',').map(value => Number(value.trim())), buttons);
    if (handLines.length < handLimit) assert.ok(stacks.filter(value => value > 0).length < 2,
        'early stop is valid only after all but one seat is busted');
    return {seed: seed.toString(), seats, startingStack, smallBlind, bigBlind, minimumOpen,
        buttonOffset: buttonOffset.toString(), firstButton, handLimit, hands: handLines.length,
        buttons, dealSeeds: dealSeeds.map(String), finalStacks: stacks};
}

if (require.main === module) {
    try {
        const result = audit(process.argv[2]);
        console.log(JSON.stringify(result, null, 2));
    } catch (error) {
        console.error(error.stack || String(error));
        process.exitCode = 1;
    }
}

module.exports = {audit};
