const fs = require('node:fs');
const path = require('node:path');

function summarize(root) {
  const groups = new Map();
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const match = /^(standard|stress)-(.+)-button(\d+)$/.exec(entry.name);
    if (!match) continue;
    const [, profile, style, offsetRaw] = match;
    const offset = +offsetRaw;
    const comparison = JSON.parse(fs.readFileSync(path.join(root, entry.name, 'comparison.json'), 'utf8'));
    const pressure = JSON.parse(fs.readFileSync(path.join(root, entry.name, 'pressure-sizing.json'), 'utf8'));
    for (const [seatsRaw, positions] of Object.entries(comparison.resultsByCandidateInitialPosition ?? {})) {
      const seats = +seatsRaw;
      // Offsets beyond the table size repeat an initial button; omit them so they cannot overweight a seat.
      if (offset >= seats) continue;
      for (const [positionRaw, row] of Object.entries(positions)) {
        const key = [profile, style, seats, +positionRaw].join('|');
        const total = groups.get(key) ?? { profile, style, seats, initialPositionFromButton: +positionRaw,
          pairedSeatRuns: 0, candidateWins: 0, controlWins: 0, sameTournamentWinner: 0,
          changedTournamentWinner: 0, candidateFinishWeighted: 0, controlFinishWeighted: 0,
        candidateTrajectoryWeighted: 0, controlTrajectoryWeighted: 0,
        candidatePressureMultipleWeighted: 0, candidatePressureSamples: 0, candidateOverPotActions: 0,
        controlPressureMultipleWeighted: 0, controlPressureSamples: 0, controlOverPotActions: 0,
        candidateAggressiveHands: 0, candidateAggressiveActions: 0, candidateFoldWinsAfterAggression: 0,
        candidateOpponentFoldsAfterAggression: 0, controlAggressiveHands: 0, controlAggressiveActions: 0,
        controlFoldWinsAfterAggression: 0, controlOpponentFoldsAfterAggression: 0 };
        const n = row.pairedSeatRuns;
        total.pairedSeatRuns += n;
        total.candidateWins += row.candidateWins;
        total.controlWins += row.controlWins;
        total.sameTournamentWinner += row.sameTournamentWinner;
        total.changedTournamentWinner += row.changedTournamentWinner;
        total.candidateFinishWeighted += row.candidateFinishChips * n;
        total.controlFinishWeighted += row.controlFinishChips * n;
        total.candidateTrajectoryWeighted += row.candidateMeanStackAcrossHands * n;
        total.controlTrajectoryWeighted += row.controlMeanStackAcrossHands * n;
        for (const field of ['candidateAggressiveHands', 'candidateAggressiveActions', 'candidateFoldWinsAfterAggression',
          'candidateOpponentFoldsAfterAggression', 'controlAggressiveHands', 'controlAggressiveActions',
          'controlFoldWinsAfterAggression', 'controlOpponentFoldsAfterAggression'])
          total[field] += row[field] ?? 0;
        const pressureRow = pressure.rows.find(item => item.seats === seats
          && item.initialPositionFromButton === +positionRaw);
        if (!pressureRow) throw new Error(`${entry.name}: missing pressure metrics for ${seats} seats, position ${positionRaw}`);
        for (const side of ['candidate', 'control']) {
          const samples = pressureRow[`${side}PressureSamples`];
          if (samples) total[`${side}PressureMultipleWeighted`] += pressureRow[`${side}MeanPressurePotMultiple`] * samples;
          total[`${side}PressureSamples`] += samples;
          total[`${side}OverPotActions`] += pressureRow[`${side}OverPotActions`];
        }
        groups.set(key, total);
      }
    }
  }
  return [...groups.values()].map(row => {
    const n = row.pairedSeatRuns;
    row.candidateMeanFinishChips = row.candidateFinishWeighted / n;
    row.controlMeanFinishChips = row.controlFinishWeighted / n;
    row.meanFinishChipDelta = row.candidateMeanFinishChips - row.controlMeanFinishChips;
    row.candidateMeanStackAcrossHands = row.candidateTrajectoryWeighted / n;
    row.controlMeanStackAcrossHands = row.controlTrajectoryWeighted / n;
    row.meanTrajectoryChipDelta = row.candidateMeanStackAcrossHands - row.controlMeanStackAcrossHands;
    row.candidateWinRate = row.candidateWins / n;
    row.controlWinRate = row.controlWins / n;
    row.candidateMeanPressurePotMultiple = row.candidatePressureSamples
      ? row.candidatePressureMultipleWeighted / row.candidatePressureSamples : null;
    row.controlMeanPressurePotMultiple = row.controlPressureSamples
      ? row.controlPressureMultipleWeighted / row.controlPressureSamples : null;
    row.candidateOverPotActionRate = row.candidatePressureSamples
      ? row.candidateOverPotActions / row.candidatePressureSamples : null;
    row.controlOverPotActionRate = row.controlPressureSamples
      ? row.controlOverPotActions / row.controlPressureSamples : null;
    row.candidateFoldWinAfterAggressionRate = row.candidateAggressiveHands
      ? row.candidateFoldWinsAfterAggression / row.candidateAggressiveHands : 0;
    row.controlFoldWinAfterAggressionRate = row.controlAggressiveHands
      ? row.controlFoldWinsAfterAggression / row.controlAggressiveHands : 0;
    row.candidateOpponentFoldsPerAggressiveHand = row.candidateAggressiveHands
      ? row.candidateOpponentFoldsAfterAggression / row.candidateAggressiveHands : 0;
    delete row.candidateFinishWeighted;
    delete row.controlFinishWeighted;
    delete row.candidateTrajectoryWeighted;
    delete row.controlTrajectoryWeighted;
    delete row.candidatePressureMultipleWeighted;
    delete row.controlPressureMultipleWeighted;
    return row;
  }).sort((a, b) => a.profile.localeCompare(b.profile) || a.style.localeCompare(b.style)
    || a.seats - b.seats || a.initialPositionFromButton - b.initialPositionFromButton);
}

try {
  const [root, outputPath] = process.argv.slice(2);
  if (!root || process.argv.length > 4)
    throw new Error('Usage: node scripts/summarize-button-positions.cjs evidence-directory [summary.csv]');
  const rows = summarize(root);
  if (!rows.length) throw new Error('No audited comparison.json files with position metrics found');
  if (outputPath) {
    if (fs.existsSync(outputPath)) throw new Error(`Refusing to overwrite ${outputPath}`);
    const fields = Object.keys(rows[0]);
    const csv = [fields.join(','), ...rows.map(row => fields.map(field => JSON.stringify(row[field] ?? '')).join(','))].join('\n') + '\n';
    fs.writeFileSync(outputPath, csv, { flag: 'wx' });
    console.log(`Wrote ${rows.length} position rows to ${outputPath}`);
  } else console.log(JSON.stringify({ rows, caveat: 'Only offsets unique modulo table size are included; stacked chips and wins are noisy tournament outcomes, not EV.' }, null, 2));
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
