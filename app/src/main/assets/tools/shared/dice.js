// Standard tabletop dice notation: "2d6+3", "4d6dl1", "2d20kh1", "d100", plus
// exploding ("d6!", "d6!5", "d6!>=5") and success-counting ("6d10>=7"). Shared
// by the Dice Bot and Custom Dice bubbles - the only real differences between
// those two tools are in how they build up the notation string (a fixed
// d4-d100 grid with Adv/Dis vs. a saved, persisted list of custom dice), not
// in how a roll is parsed, rolled, or rendered.

const MAX_DICE_PER_TERM = 200;
const MAX_SIDES = 1000;
const MAX_EXPLOSIONS_PER_TERM = 100;
const TERM_REGEX = /\s*([+-])?\s*(?:(\d*)d(\d+|%)(!(?:(>=|<=|>|<|=)?(\d+))?)?([kd][hl]\d*)?(?:(>=|<=|>|<|=)(\d+))?|(\d+))/gi;

const COMPARATORS = {
  '>=': (a, b) => a >= b,
  '<=': (a, b) => a <= b,
  '>': (a, b) => a > b,
  '<': (a, b) => a < b,
  '=': (a, b) => a === b,
};

function rollNotation(raw) {
  const input = raw.trim();
  if (!input) throw new Error('Type a roll, like 2d6+3.');

  const terms = [];
  let total = 0;
  let cursor = 0;
  TERM_REGEX.lastIndex = 0;
  let match;
  while ((match = TERM_REGEX.exec(input)) !== null) {
    if (match.index !== cursor) {
      throw new Error(`Couldn't parse "${input.slice(cursor)}".`);
    }
    cursor = match.index + match[0].length;

    const sign = match[1] === '-' ? -1 : 1;
    const sidesRaw = match[3];
    if (sidesRaw !== undefined) {
      const count = match[2] ? parseInt(match[2], 10) : 1;
      const sides = sidesRaw === '%' ? 100 : parseInt(sidesRaw, 10);
      if (count < 1 || count > MAX_DICE_PER_TERM) {
        throw new Error(`Dice count must be 1-${MAX_DICE_PER_TERM}.`);
      }
      if (sides < 1 || sides > MAX_SIDES) {
        throw new Error(`Sides must be 1-${MAX_SIDES}.`);
      }

      // Explode: a die that meets the threshold rolls again, and the new die
      // can explode again too (capped, so "d2!1" - a die that always
      // qualifies - can't hang the page rolling forever).
      let explodeCompare = null;
      let explodeThreshold = null;
      if (match[4]) {
        explodeThreshold = match[6] ? parseInt(match[6], 10) : sides;
        if (explodeThreshold < 1 || explodeThreshold > sides) {
          throw new Error(`Explode threshold must be 1-${sides}.`);
        }
        explodeCompare = COMPARATORS[match[5] || '>='];
      }

      const dice = Array.from({ length: count }, () => ({
        value: 1 + Math.floor(Math.random() * sides),
        dropped: false,
        exploded: false,
      }));

      if (explodeCompare) {
        let frontier = dice.slice();
        let explosions = 0;
        while (frontier.length && explosions < MAX_EXPLOSIONS_PER_TERM) {
          const next = [];
          for (const d of frontier) {
            if (explosions >= MAX_EXPLOSIONS_PER_TERM) break;
            if (explodeCompare(d.value, explodeThreshold)) {
              d.exploded = true;
              const nd = { value: 1 + Math.floor(Math.random() * sides), dropped: false, exploded: false };
              dice.push(nd);
              next.push(nd);
              explosions++;
            }
          }
          frontier = next;
        }
      }

      applyKeepDrop(dice, match[7]);

      // Success-counting (e.g. "6d10>=7"): the term's value becomes how many
      // surviving dice meet the threshold, instead of their sum.
      let subtotal;
      if (match[8]) {
        const countCompare = COMPARATORS[match[8]];
        const threshold = parseInt(match[9], 10);
        const successes = dice.filter((d) => !d.dropped && countCompare(d.value, threshold)).length;
        subtotal = sign * successes;
      } else {
        subtotal = sign * dice.filter((d) => !d.dropped).reduce((s, d) => s + d.value, 0);
      }
      terms.push({ kind: 'dice', text: match[0].trim(), dice, subtotal });
      total += subtotal;
    } else {
      const value = sign * parseInt(match[10], 10);
      terms.push({ kind: 'flat', text: match[0].trim(), value, subtotal: value });
      total += value;
    }
  }

  if (cursor !== input.length) {
    throw new Error(`Couldn't parse "${input.slice(cursor)}".`);
  }
  if (terms.length === 0) {
    throw new Error('Type a roll, like 2d6+3.');
  }
  return { notation: input, terms, total };
}

function applyKeepDrop(dice, modifierRaw) {
  if (!modifierRaw) return;
  const kind = modifierRaw.slice(0, 2).toLowerCase();
  const count = Math.min(
    dice.length,
    modifierRaw.length > 2 ? parseInt(modifierRaw.slice(2), 10) : 1,
  );
  const order = dice.map((_, i) => i).sort((a, b) => dice[b].value - dice[a].value);
  let dropIndices;
  if (kind === 'kh') dropIndices = order.slice(count);
  else if (kind === 'kl') dropIndices = order.slice().reverse().slice(count);
  else if (kind === 'dh') dropIndices = order.slice(0, count);
  else if (kind === 'dl') dropIndices = order.slice().reverse().slice(0, count);
  else dropIndices = [];
  dropIndices.forEach((i) => { dice[i].dropped = true; });
}

function breakdown(result) {
  let out = '';
  result.terms.forEach((term, index) => {
    const negative = term.subtotal < 0 || term.text.startsWith('-');
    if (index > 0) out += negative ? ' - ' : ' + ';
    else if (negative) out += '-';
    if (term.kind === 'dice') {
      out += '[' + term.dice.map((d) => {
        const s = d.exploded ? `${d.value}!` : `${d.value}`;
        return d.dropped ? `(${s})` : s;
      }).join(', ') + ']';
    } else {
      out += Math.abs(term.value);
    }
  });
  out += ' = ' + result.total;
  return out;
}

// ---- roll builder: parses/edits a notation string without rolling it ----

function parseBuilderTerms(raw) {
  const input = raw.trim();
  if (!input) return [];
  const terms = [];
  let cursor = 0;
  TERM_REGEX.lastIndex = 0;
  let match;
  while ((match = TERM_REGEX.exec(input)) !== null) {
    if (match.index !== cursor) return null;
    cursor = match.index + match[0].length;
    const sign = match[1] === '-' ? -1 : 1;
    const sidesRaw = match[3];
    if (sidesRaw !== undefined) {
      const count = match[2] ? parseInt(match[2], 10) : 1;
      const sides = sidesRaw === '%' ? 100 : parseInt(sidesRaw, 10);
      terms.push({
        kind: 'dice', sign, count, sides,
        explode: match[4] || null,
        keepDrop: match[7] || null,
        successCmp: match[8] ? match[8] + match[9] : null,
      });
    } else {
      terms.push({ kind: 'flat', value: sign * parseInt(match[10], 10) });
    }
  }
  if (cursor !== input.length) return null;
  return terms;
}

function normalizeKeepDrop(keepDrop) {
  if (!keepDrop) return null;
  const m = keepDrop.toLowerCase();
  return m.length > 2 ? m : m + '1';
}

// Only a fully plain die (no explode/keep-drop/success-count) is safe to
// merge by incrementing its count - anything else changes what it means if
// counts are combined (2d20kh1 + 2d20kh1 is NOT the same roll as 4d20kh1).
function isPlainDiceTerm(t) {
  return t.kind === 'dice' && !t.explode && !t.keepDrop && !t.successCmp && t.sign > 0;
}

function renderBuilderTerms(terms) {
  let out = '';
  terms.forEach((term, index) => {
    let sign, magnitude;
    if (term.kind === 'dice') {
      sign = term.sign;
      magnitude = (term.count === 1 ? '' : term.count) + 'd' + term.sides +
        (term.explode || '') + (term.keepDrop || '') + (term.successCmp || '');
    } else {
      sign = term.value < 0 ? -1 : 1;
      magnitude = String(Math.abs(term.value));
    }
    if (index === 0) out += sign < 0 ? '-' + magnitude : magnitude;
    else out += (sign < 0 ? ' - ' : ' + ') + magnitude;
  });
  return out;
}

// ---- die icons: shared between the fixed grid (Dice Bot) and the saved-dice
// grid (Custom Dice) ----

const GEM_VAR = {
  4: '--gem-d4', 6: '--gem-d6', 8: '--gem-d8', 10: '--gem-d10',
  12: '--gem-d12', 20: '--gem-d20', 100: '--gem-d100',
};
const ICON_POINTS = {
  4: '12,4 20,19 4,19',
  6: '6,6 18,6 18,18 6,18',
  8: '12,3 21,12 12,21 3,12',
  10: '12,3 19,9 15,21 9,21 5,9',
  12: '12,3 21,9.5 17.5,20 6.5,20 3,9.5',
  20: '12,3 20,7.5 20,16.5 12,21 4,16.5 4,7.5',
};
function diceSvg(sides) {
  if (sides === 100) {
    return '<svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6"/>' +
      '<text x="12" y="15.5" text-anchor="middle" font-size="8" fill="currentColor" font-family="IBM Plex Mono, monospace">%</text></svg>';
  }
  // Non-standard side counts (d13, d3, d30...) get a generic die silhouette -
  // drawing an accurate N-gon for arbitrary N isn't worth the complexity.
  const points = ICON_POINTS[sides] || ICON_POINTS[20];
  return '<svg viewBox="0 0 24 24" fill="none"><polygon points="' + points +
    '" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>';
}
