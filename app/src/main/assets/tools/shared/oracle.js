// Shared by the four Ironsworn oracle-table bubbles (Ask the Oracle, Plot &
// Twist, Name Generator, Settlement & Place): rolling 1d100 against a table
// of {min, max, text} rows, and wiring up a grid of tap-to-roll tiles. What
// each tool actually rolls and how it labels the result is real per-tool
// content, not duplication, so that part stays in each tool's own page.

function rollTable(table) {
  const roll = 1 + Math.floor(Math.random() * 100);
  const row = table.rows.find((r) => roll >= r.min && roll <= r.max);
  return { roll, text: row.text };
}

// buttons: array of { label, handler }, where handler() performs the roll,
// logs a card, and reports to native - it's the one thing that genuinely
// differs per tool (which table(s), how the result is composed and labeled).
function initOracleGrid(grid, buttons) {
  buttons.forEach(({ label, handler }) => {
    const btn = document.createElement('button');
    btn.className = 'tile';
    btn.textContent = label;
    btn.addEventListener('click', () => {
      tapFeedback(btn);
      handler();
    });
    grid.appendChild(btn);
  });
}
