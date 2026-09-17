// Shared by the Ironsworn Oracles tool's categories (Ask the Oracle, Plot &
// Twist, Name Generator, Settlement & Place): rolling 1d100 against a table
// of {min, max, text} rows, and wiring up a grid of tap-to-roll tiles. What
// each category actually rolls and how it labels the result is real content,
// not duplication, so that part stays in the tool's own page.

// Every Ironsworn table is d100, but Starforged has a few oddballs (a d10
// table, some d20 tables) - parse the table's own dice size instead of
// assuming 100, falling back to 100 for any table that omits it.
function tableDiceMax(table) {
  const match = /^\d+d(\d+)$/.exec(table.dice || '');
  return match ? parseInt(match[1], 10) : 100;
}

// row is the full matched row, not just its text - most tools only destructure
// { roll, text }, but a table_text2-style table (a result plus a second
// "summary" column, e.g. Starforged's Faction Influence) needs row.text2 too.
function rollTable(table) {
  const roll = 1 + Math.floor(Math.random() * tableDiceMax(table));
  const row = table.rows.find((r) => roll >= r.min && roll <= r.max);
  return { roll, text: row.text, row };
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
