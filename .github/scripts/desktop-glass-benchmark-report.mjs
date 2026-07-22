// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

import assert from 'node:assert/strict'
import { lstatSync, readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

const ARTIFACT_KEYS = [
  'schemaVersion',
  'suiteId',
  'repository',
  'baseSha',
  'headSha',
  'scenarios',
  'status',
  'diagnostic',
]
const SCENARIO_KEYS = [
  'id',
  'baseProtocolVersion',
  'headProtocolVersion',
  'comparable',
  'baseRender',
  'headRender',
  'baseInterval',
  'headInterval',
  'renderPairedDeltaPercent',
  'intervalPairedDeltaPercent',
  'blocks',
]
const METRIC_KEYS = [
  'sampleCount',
  'p50Nanos',
  'p95Nanos',
  'p99Nanos',
  'above16MillisCount',
  'above16MillisPercent',
  'above33MillisCount',
  'above33MillisPercent',
  'robustVariationPercent',
  'noisy',
]
const BLOCK_KEYS = [
  'schemaVersion',
  'suiteId',
  'scenarioId',
  'protocolVersion',
  'revision',
  'round',
  'order',
  'environment',
  'workloadDurationNanos',
  'samples',
]
const ENVIRONMENT_KEYS = [
  'osName',
  'osVersion',
  'architecture',
  'cpu',
  'memoryBytes',
  'javaVendor',
  'javaVersion',
  'composeVersion',
  'skikoVersion',
  'renderApi',
  'framebufferWidth',
  'framebufferHeight',
  'contentScale',
  'refreshRateHz',
  'runnerImage',
  'runnerImageVersion',
]
const SAMPLE_KEYS = ['renderDurationNanos', 'callbackIntervalNanos']
const REQUIRED_METADATA_KEYS = [
  'osName',
  'osVersion',
  'architecture',
  'cpu',
  'javaVendor',
  'javaVersion',
  'composeVersion',
  'skikoVersion',
  'renderApi',
]
const REGISTERED_SCENARIOS = ['playground_drag', 'pointer_sweep']
const IDENTIFIER_PATTERN = /^[a-z][a-z0-9_]{0,63}$/
const REPOSITORY_SEGMENT_PATTERN = /^[A-Za-z0-9_.-]{1,100}$/
const SHA_PATTERN = /^[0-9a-f]{40}$/
const MARKER = '<!-- desktop-glass-benchmark -->'
const MAX_ARTIFACT_BYTES = 5 * 1024 * 1024
const MAX_DIAGNOSTIC_BYTES = 2_048
const MAX_METADATA_BYTES = 256
const MAX_SAMPLE_COUNT = 100_000
const SIXTEEN_MILLIS_NANOS = 16_666_667
const THIRTY_THREE_MILLIS_NANOS = 33_333_333
const FLOAT_ABSOLUTE_TOLERANCE = 1e-9
const FLOAT_RELATIVE_TOLERANCE = 1e-12
const trustedArtifacts = new WeakSet()

export function parseArtifactBuffer(buffer, expected) {
  assert.ok(Buffer.isBuffer(buffer), 'artifact input must be a Buffer')
  assert.ok(buffer.byteLength <= MAX_ARTIFACT_BYTES, 'artifact exceeds five MiB')

  let text
  try {
    text = new TextDecoder('utf-8', { fatal: true }).decode(buffer)
  } catch (failure) {
    throw new Error('artifact is not valid UTF-8', { cause: failure })
  }

  let value
  try {
    rejectDuplicateJsonKeys(text)
    value = JSON.parse(text)
  } catch (failure) {
    throw new Error(`artifact is not valid JSON: ${failure.message}`, { cause: failure })
  }
  return validateArtifact(value, expected)
}

function rejectDuplicateJsonKeys(text) {
  let index = 0
  const numberPattern = /-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/y

  parseValue(0)
  skipWhitespace()
  assert.equal(index, text.length, 'unexpected trailing JSON content')

  function parseValue(depth) {
    assert.ok(depth <= 64, 'JSON nesting exceeds the limit')
    skipWhitespace()
    const character = text[index]
    if (character === '{') {
      parseObject(depth + 1)
    } else if (character === '[') {
      parseArray(depth + 1)
    } else if (character === '"') {
      parseString()
    } else if (character === 't' && text.startsWith('true', index)) {
      index += 4
    } else if (character === 'f' && text.startsWith('false', index)) {
      index += 5
    } else if (character === 'n' && text.startsWith('null', index)) {
      index += 4
    } else {
      parseNumber()
    }
  }

  function parseObject(depth) {
    index += 1
    skipWhitespace()
    const keys = new Set()
    if (text[index] === '}') {
      index += 1
      return
    }
    while (true) {
      assert.equal(text[index], '"', 'JSON object key must be a string')
      const key = parseString()
      assert.ok(!keys.has(key), `duplicate JSON key: ${key}`)
      keys.add(key)
      skipWhitespace()
      assert.equal(text[index], ':', 'JSON object key must be followed by a colon')
      index += 1
      parseValue(depth)
      skipWhitespace()
      if (text[index] === '}') {
        index += 1
        return
      }
      assert.equal(text[index], ',', 'JSON object entries must be comma-separated')
      index += 1
      skipWhitespace()
    }
  }

  function parseArray(depth) {
    index += 1
    skipWhitespace()
    if (text[index] === ']') {
      index += 1
      return
    }
    while (true) {
      parseValue(depth)
      skipWhitespace()
      if (text[index] === ']') {
        index += 1
        return
      }
      assert.equal(text[index], ',', 'JSON array entries must be comma-separated')
      index += 1
    }
  }

  function parseString() {
    const start = index
    index += 1
    while (index < text.length) {
      const character = text[index]
      const code = text.charCodeAt(index)
      if (character === '"') {
        index += 1
        return JSON.parse(text.slice(start, index))
      }
      assert.ok(code >= 0x20, 'JSON string contains a control character')
      if (character === '\\') {
        index += 1
        assert.ok(index < text.length, 'JSON string has an incomplete escape')
        if (text[index] === 'u') {
          assert.match(text.slice(index + 1, index + 5), /^[0-9a-fA-F]{4}$/, 'invalid JSON Unicode escape')
          index += 4
        } else {
          assert.ok('"\\/bfnrt'.includes(text[index]), 'invalid JSON string escape')
        }
      }
      index += 1
    }
    assert.fail('unterminated JSON string')
  }

  function parseNumber() {
    numberPattern.lastIndex = index
    const match = numberPattern.exec(text)
    assert.ok(match, 'invalid JSON value')
    index += match[0].length
  }

  function skipWhitespace() {
    while (index < text.length && ' \t\r\n'.includes(text[index])) index += 1
  }
}

export function validateArtifact(value, expected) {
  validateExpectedIdentity(expected)
  exactKeys(value, ARTIFACT_KEYS, 'artifact')
  assert.equal(value.schemaVersion, 1, 'artifact schema version must be 1')
  assert.equal(value.suiteId, 'glass', 'artifact suite must be glass')
  validateRepository(value.repository, 'artifact repository')
  validateSha(value.baseSha, 'artifact base SHA')
  validateSha(value.headSha, 'artifact head SHA')
  assert.equal(value.repository, expected.repository, 'artifact repository does not match expected repository')
  assert.equal(value.baseSha, expected.baseSha, 'artifact base SHA does not match expected base SHA')
  assert.equal(value.headSha, expected.headSha, 'artifact head SHA does not match expected head SHA')
  assert.ok(value.status === 'complete' || value.status === 'failed', 'artifact status is invalid')
  assert.ok(Array.isArray(value.scenarios), 'artifact scenarios must be an array')

  if (value.status === 'failed') {
    assert.equal(value.scenarios.length, 0, 'failed artifact must contain no scenarios')
    validateBoundedString(value.diagnostic, MAX_DIAGNOSTIC_BYTES, 'failed artifact diagnostic')
    const artifact = deepFreeze({
      schemaVersion: 1,
      suiteId: 'glass',
      repository: value.repository,
      baseSha: value.baseSha,
      headSha: value.headSha,
      scenarios: [],
      status: 'failed',
      diagnostic: value.diagnostic,
    })
    trustedArtifacts.add(artifact)
    return artifact
  }

  assert.equal(value.diagnostic, null, 'complete artifact must have a null diagnostic')
  const scenarioIds = value.scenarios.map(scenario => scenario?.id)
  assert.deepEqual(
    [...new Set(scenarioIds)].sort(),
    REGISTERED_SCENARIOS,
    'complete artifact scenario set must exactly match the registered scenarios',
  )
  assert.deepEqual(scenarioIds, REGISTERED_SCENARIOS, 'complete artifact scenario order is not deterministic')

  const sampleCounter = { count: 0 }
  const validatedScenarios = value.scenarios.map(scenario => validateScenario(scenario, sampleCounter))
  const modes = new Set(validatedScenarios.map(scenario => scenario.blocks.length === 12 ? 'abba' : 'head-only'))
  assert.equal(modes.size, 1, 'all scenarios must use the same comparison mode')

  const artifact = deepFreeze({
    schemaVersion: 1,
    suiteId: 'glass',
    repository: value.repository,
    baseSha: value.baseSha,
    headSha: value.headSha,
    scenarios: validatedScenarios,
    status: 'complete',
    diagnostic: null,
  })
  trustedArtifacts.add(artifact)
  return artifact
}

export function renderComment(artifact, runUrl) {
  assert.ok(trustedArtifacts.has(artifact), 'renderComment requires a validated artifact')
  validateRunUrl(runUrl, artifact.repository)

  if (artifact.status === 'failed') {
    return [
      MARKER,
      '## Desktop Glass benchmark',
      '',
      `**Infrastructure failure:** ${escapeMarkdown(artifact.diagnostic)}`,
      '',
      'The observational Desktop Glass benchmark did not produce usable measurements; this is an infrastructure failure, not a performance gate.',
      `[Workflow run](${runUrl})`,
      '',
    ].join('\n')
  }

  const headEnvironment = artifact.scenarios[0].blocks.find(block => block.revision === 'head').environment
  const runner = headEnvironment.runnerImage ?? `${headEnvironment.osName} ${headEnvironment.osVersion}`
  const protocolLabel = renderProtocolLabel(artifact.scenarios)
  const lines = [
    MARKER,
    '## Desktop Glass benchmark',
    '',
    `${escapeMarkdown(runner)} · ${escapeMarkdown(headEnvironment.cpu)} · Metal · ${protocolLabel}`,
    '',
    '| Scenario | Revision | Render callback duration p50 | Render callback duration p95 | Callback interval p95 | Callback interval >16.67 ms | Render delta | Noise |',
    '|---|---:|---:|---:|---:|---:|---:|---:|',
  ]

  for (const scenario of artifact.scenarios) {
    if (scenario.baseRender !== null) {
      lines.push(renderMetricRow(scenario, 'base'))
    }
    lines.push(renderMetricRow(scenario, 'head'))
  }

  lines.push(
    '',
    'These Skiko render callback duration and callback interval values are observational; they are not GPU completion or presentation times and do not gate this pull request.',
    `[Workflow run](${runUrl})`,
    '',
  )
  return lines.join('\n')
}

function validateExpectedIdentity(expected) {
  exactKeys(expected, ['repository', 'baseSha', 'headSha'], 'expected identity')
  validateRepository(expected.repository, 'expected repository')
  validateSha(expected.baseSha, 'expected base SHA')
  validateSha(expected.headSha, 'expected head SHA')
}

function validateScenario(value, sampleCounter) {
  exactKeys(value, SCENARIO_KEYS, 'scenario')
  assert.equal(typeof value.id, 'string', 'scenario id must be a string')
  assert.match(value.id, IDENTIFIER_PATTERN, 'scenario id is invalid')
  assert.ok(REGISTERED_SCENARIOS.includes(value.id), `unknown scenario id: ${value.id}`)
  assert.ok(Array.isArray(value.blocks), `scenario ${value.id} blocks must be an array`)
  assert.ok(value.blocks.length <= 12, `scenario ${value.id} has too many blocks`)

  validateOptionalPositiveInteger(value.baseProtocolVersion, `${value.id} base protocol`)
  validatePositiveInteger(value.headProtocolVersion, `${value.id} head protocol`)
  assert.equal(typeof value.comparable, 'boolean', `${value.id} comparability must be boolean`)
  validateOptionalMetric(value.baseRender, `${value.id}.baseRender`)
  validateMetric(value.headRender, `${value.id}.headRender`)
  validateOptionalMetric(value.baseInterval, `${value.id}.baseInterval`)
  validateMetric(value.headInterval, `${value.id}.headInterval`)
  validateOptionalFiniteNumber(value.renderPairedDeltaPercent, `${value.id} render paired delta`)
  validateOptionalFiniteNumber(value.intervalPairedDeltaPercent, `${value.id} interval paired delta`)

  const blocks = value.blocks.map((block, index) =>
    validateBlock(block, value.id, index, sampleCounter))
  const sortedBlocks = [...blocks].sort(compareBlocks)
  assert.deepEqual(blocks, sortedBlocks, `scenario ${value.id} block order is not deterministic`)
  const mode = validateSlots(value.id, blocks)
  const baseBlocks = blocks.filter(block => block.revision === 'base')
  const headBlocks = blocks.filter(block => block.revision === 'head')
  const baseProtocols = [...new Set(baseBlocks.map(block => block.protocolVersion))]
  const headProtocols = [...new Set(headBlocks.map(block => block.protocolVersion))]
  assert.ok(baseProtocols.length <= 1, `scenario ${value.id} mixes base protocol versions`)
  assert.equal(headProtocols.length, 1, `scenario ${value.id} mixes head protocol versions`)

  const baseProtocolVersion = baseProtocols[0] ?? null
  const headProtocolVersion = headProtocols[0]
  const comparable = mode === 'abba' && baseProtocolVersion === headProtocolVersion
  assert.equal(
    value.baseProtocolVersion,
    baseProtocolVersion,
    `scenario ${value.id} base protocol field does not match raw blocks`,
  )
  assert.equal(
    value.headProtocolVersion,
    headProtocolVersion,
    `scenario ${value.id} head protocol field does not match raw blocks`,
  )
  assert.equal(
    value.comparable,
    comparable,
    `scenario ${value.id} comparability does not match raw protocols`,
  )

  const baseRender = baseBlocks.length === 0 ? null : summarizeMetric(baseBlocks, renderValues)
  const headRender = summarizeMetric(headBlocks, renderValues)
  const baseInterval = baseBlocks.length === 0 ? null : summarizeMetric(baseBlocks, intervalValues)
  const headInterval = summarizeMetric(headBlocks, intervalValues)
  assertOptionalMetricMatches(value.baseRender, baseRender, `${value.id}.baseRender`)
  assertMetricMatches(value.headRender, headRender, `${value.id}.headRender`)
  assertOptionalMetricMatches(value.baseInterval, baseInterval, `${value.id}.baseInterval`)
  assertMetricMatches(value.headInterval, headInterval, `${value.id}.headInterval`)

  const renderPairedDeltaPercent = comparable
    ? pairedMetricDelta(baseBlocks, headBlocks, renderValues, value.id, 'render')
    : null
  const intervalPairedDeltaPercent = comparable
    ? pairedMetricDelta(baseBlocks, headBlocks, intervalValues, value.id, 'interval')
    : null
  assertOptionalFloatEqual(
    value.renderPairedDeltaPercent,
    renderPairedDeltaPercent,
    `${value.id} render paired delta mismatch`,
  )
  assertOptionalFloatEqual(
    value.intervalPairedDeltaPercent,
    intervalPairedDeltaPercent,
    `${value.id} interval paired delta mismatch`,
  )

  return {
    id: value.id,
    baseProtocolVersion,
    headProtocolVersion,
    comparable,
    baseRender,
    headRender,
    baseInterval,
    headInterval,
    renderPairedDeltaPercent,
    intervalPairedDeltaPercent,
    blocks,
  }
}

function validateBlock(value, scenarioId, index, sampleCounter) {
  const path = `${scenarioId}.blocks[${index}]`
  exactKeys(value, BLOCK_KEYS, path)
  assert.equal(value.schemaVersion, 1, `${path} schema version must be 1`)
  assert.equal(value.suiteId, 'glass', `${path} suite must be glass`)
  assert.equal(value.scenarioId, scenarioId, `${path} scenario id does not match its scenario`)
  validatePositiveInteger(value.protocolVersion, `${path}.protocolVersion`)
  assert.ok(value.revision === 'base' || value.revision === 'head', `${path} revision is invalid`)
  validateSafeInteger(value.round, `${path}.round`, 0)
  assert.ok(value.round <= 2, `${path} round must be from 0 through 2`)
  validateSafeInteger(value.order, `${path}.order`, 0)
  assert.ok(value.order <= 3, `${path} order must be from 0 through 3`)
  validateSafeInteger(value.workloadDurationNanos, `${path}.workloadDurationNanos`, 0)
  const environment = validateEnvironment(value.environment, `${path}.environment`)
  assert.ok(Array.isArray(value.samples), `${path}.samples must be an array`)
  assert.ok(value.samples.length > 0, `${path} samples must not be empty`)
  sampleCounter.count += value.samples.length
  assert.ok(sampleCounter.count <= MAX_SAMPLE_COUNT, 'artifact has too many samples')
  const samples = value.samples.map((sample, sampleIndex) =>
    validateSample(sample, `${path}.samples[${sampleIndex}]`))
  assert.ok(
    samples.some(sample => sample.callbackIntervalNanos !== null),
    `${path} must contain a callback interval sample`,
  )
  return {
    schemaVersion: 1,
    suiteId: 'glass',
    scenarioId,
    protocolVersion: value.protocolVersion,
    revision: value.revision,
    round: value.round,
    order: value.order,
    environment,
    workloadDurationNanos: value.workloadDurationNanos,
    samples,
  }
}

function validateEnvironment(value, path) {
  exactKeys(value, ENVIRONMENT_KEYS, path)
  for (const key of REQUIRED_METADATA_KEYS) {
    validateBoundedString(value[key], MAX_METADATA_BYTES, `${path}.${key}`)
  }
  validateOptionalBoundedString(value.runnerImage, MAX_METADATA_BYTES, `${path}.runnerImage`)
  validateOptionalBoundedString(
    value.runnerImageVersion,
    MAX_METADATA_BYTES,
    `${path}.runnerImageVersion`,
  )
  validatePositiveInteger(value.memoryBytes, `${path}.memoryBytes`)
  validatePositiveInteger(value.framebufferWidth, `${path}.framebufferWidth`)
  validatePositiveInteger(value.framebufferHeight, `${path}.framebufferHeight`)
  validatePositiveInteger(value.refreshRateHz, `${path}.refreshRateHz`)
  validateFiniteNumber(value.contentScale, `${path}.contentScale`)
  assert.ok(value.contentScale > 0, `${path}.contentScale must be positive`)
  assert.equal(value.renderApi, 'METAL', `${path}.renderApi must be METAL`)
  assert.equal(value.framebufferWidth, 1280, `${path}.framebufferWidth must be 1280`)
  assert.equal(value.framebufferHeight, 720, `${path}.framebufferHeight must be 720`)
  return Object.fromEntries(ENVIRONMENT_KEYS.map(key => [key, value[key]]))
}

function validateSample(value, path) {
  exactKeys(value, SAMPLE_KEYS, path)
  validateSafeInteger(value.renderDurationNanos, `${path}.renderDurationNanos`, 0)
  if (value.callbackIntervalNanos !== null) {
    validateSafeInteger(value.callbackIntervalNanos, `${path}.callbackIntervalNanos`, 0)
  }
  return {
    renderDurationNanos: value.renderDurationNanos,
    callbackIntervalNanos: value.callbackIntervalNanos,
  }
}

function validateSlots(scenarioId, blocks) {
  const slots = blocks.map(block => `${block.revision}:${block.round}:${block.order}`)
  assert.equal(new Set(slots).size, slots.length, `scenario ${scenarioId} contains a duplicate slot`)
  const expectedAbba = []
  const expectedHeadOnly = []
  for (let round = 0; round < 3; round += 1) {
    expectedAbba.push(`base:${round}:0`, `head:${round}:1`, `head:${round}:2`, `base:${round}:3`)
    expectedHeadOnly.push(`head:${round}:1`, `head:${round}:2`)
  }
  if (arraysEqual(slots, expectedAbba)) return 'abba'
  if (arraysEqual(slots, expectedHeadOnly)) return 'head-only'
  assert.fail(`scenario ${scenarioId} does not contain the required ABBA or head-only slots`)
}

function validateMetric(value, path) {
  exactKeys(value, METRIC_KEYS, path)
  for (const key of [
    'sampleCount',
    'p50Nanos',
    'p95Nanos',
    'p99Nanos',
    'above16MillisCount',
    'above33MillisCount',
  ]) {
    validateSafeInteger(value[key], `${path}.${key}`, 0)
  }
  for (const key of [
    'above16MillisPercent',
    'above33MillisPercent',
    'robustVariationPercent',
  ]) {
    validateFiniteNumber(value[key], `${path}.${key}`)
  }
  assert.equal(typeof value.noisy, 'boolean', `${path}.noisy must be boolean`)
}

function validateOptionalMetric(value, path) {
  if (value !== null) validateMetric(value, path)
}

function summarizeMetric(blocks, extractValues) {
  const allValues = blocks.flatMap(extractValues)
  assert.ok(allValues.length > 0, 'metric sample set must not be empty')
  const blockMedians = blocks.map(block => median(extractValues(block)))
  const robustVariationPercent = robustRelativeVariationPercent(blockMedians)
  assert.ok(Number.isFinite(robustVariationPercent), 'metric variation must be finite')
  const above16MillisCount = allValues.filter(value => value > SIXTEEN_MILLIS_NANOS).length
  const above33MillisCount = allValues.filter(value => value > THIRTY_THREE_MILLIS_NANOS).length
  return {
    sampleCount: allValues.length,
    p50Nanos: nearestRank(allValues, 0.50),
    p95Nanos: nearestRank(allValues, 0.95),
    p99Nanos: nearestRank(allValues, 0.99),
    above16MillisCount,
    above16MillisPercent: above16MillisCount / allValues.length * 100,
    above33MillisCount,
    above33MillisPercent: above33MillisCount / allValues.length * 100,
    robustVariationPercent,
    noisy: robustVariationPercent > 10,
  }
}

function pairedMetricDelta(baseBlocks, headBlocks, extractValues, scenarioId, metricName) {
  const baseMedians = baseBlocks.map(block => median(extractValues(block)))
  assert.ok(
    baseMedians.every(value => value > 0),
    `${scenarioId} ${metricName} paired delta requires a positive base block median`,
  )
  const headMedians = headBlocks.map(block => median(extractValues(block)))
  assert.equal(baseMedians.length, 6, 'paired delta requires six base blocks')
  assert.equal(headMedians.length, 6, 'paired delta requires six head blocks')
  const roundDeltas = [0, 1, 2].map(round => {
    const baseMedian = median(baseMedians.slice(round * 2, round * 2 + 2))
    const headMedian = median(headMedians.slice(round * 2, round * 2 + 2))
    return (headMedian / baseMedian - 1) * 100
  })
  const result = median(roundDeltas)
  assert.ok(Number.isFinite(result), 'paired delta must be finite')
  return result
}

function nearestRank(values, percentile) {
  const sorted = [...values].sort((left, right) => left - right)
  const rank = Math.min(sorted.length, Math.max(1, Math.ceil(percentile * sorted.length)))
  return sorted[rank - 1]
}

function robustRelativeVariationPercent(values) {
  const center = median(values)
  if (center === 0) return values.every(value => value === 0) ? 0 : 100
  return median(values.map(value => Math.abs(value - center))) / Math.abs(center) * 100
}

function median(values) {
  assert.ok(values.length > 0, 'median input must not be empty')
  const sorted = [...values].sort((left, right) => left - right)
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1
    ? sorted[middle]
    : sorted[middle - 1] / 2 + sorted[middle] / 2
}

function renderValues(block) {
  return block.samples.map(sample => sample.renderDurationNanos)
}

function intervalValues(block) {
  return block.samples
    .map(sample => sample.callbackIntervalNanos)
    .filter(value => value !== null)
}

function assertOptionalMetricMatches(actual, expected, path) {
  if (expected === null) {
    assert.equal(actual, null, `${path} summary mismatch`)
  } else {
    assert.notEqual(actual, null, `${path} summary mismatch`)
    assertMetricMatches(actual, expected, path)
  }
}

function assertMetricMatches(actual, expected, path) {
  for (const key of METRIC_KEYS) {
    if (typeof expected[key] === 'number' && !Number.isInteger(expected[key])) {
      assertFloatEqual(actual[key], expected[key], `${path} summary mismatch for ${key}`)
    } else {
      assert.equal(actual[key], expected[key], `${path} summary mismatch for ${key}`)
    }
  }
}

function assertOptionalFloatEqual(actual, expected, message) {
  if (expected === null) {
    assert.equal(actual, null, message)
  } else {
    assert.notEqual(actual, null, message)
    assertFloatEqual(actual, expected, message)
  }
}

function assertFloatEqual(actual, expected, message) {
  validateFiniteNumber(actual, message)
  const tolerance = Math.max(
    FLOAT_ABSOLUTE_TOLERANCE,
    Math.abs(expected) * FLOAT_RELATIVE_TOLERANCE,
  )
  assert.ok(Math.abs(actual - expected) <= tolerance, message)
}

function renderProtocolLabel(scenarios) {
  const protocols = new Set(scenarios.map(scenario => scenario.headProtocolVersion))
  if (protocols.size === 1) return `protocol ${scenarios[0].headProtocolVersion}`
  return `protocols ${scenarios
    .map(scenario => `${escapeMarkdown(scenario.id)} ${scenario.headProtocolVersion}`)
    .join(', ')}`
}

function renderMetricRow(scenario, revision) {
  const base = revision === 'base'
  const render = base ? scenario.baseRender : scenario.headRender
  const interval = base ? scenario.baseInterval : scenario.headInterval
  const delta = base
    ? '—'
    : renderDeltaLabel(scenario)
  const noise = render.noisy || interval.noisy ? 'noisy' : 'low'
  return [
    '',
    escapeMarkdown(scenario.id),
    base ? 'base' : 'PR',
    formatMilliseconds(render.p50Nanos),
    formatMilliseconds(render.p95Nanos),
    formatMilliseconds(interval.p95Nanos),
    `${interval.above16MillisPercent.toFixed(1)}%`,
    delta,
    noise,
    '',
  ].join(' | ')
}

function renderDeltaLabel(scenario) {
  if (scenario.baseProtocolVersion !== null && !scenario.comparable) return 'not comparable'
  if (scenario.renderPairedDeltaPercent === null) return '—'
  const value = scenario.renderPairedDeltaPercent
  return `${value > 0 ? '+' : ''}${value.toFixed(1)}%`
}

function formatMilliseconds(nanos) {
  return `${(nanos / 1_000_000).toFixed(3)} ms`
}

function escapeMarkdown(value) {
  assert.equal(typeof value, 'string', 'Markdown value must be a string')
  let escaped = ''
  for (const character of value) {
    const codePoint = character.codePointAt(0)
    if (character === '\n') {
      escaped += '\\n'
    } else if (character === '\r') {
      escaped += '\\r'
    } else if (character === '\t') {
      escaped += '\\t'
    } else if (
      codePoint < 0x20 ||
      (codePoint >= 0x7f && codePoint <= 0x9f) ||
      codePoint === 0x2028 ||
      codePoint === 0x2029 ||
      (codePoint >= 0x202a && codePoint <= 0x202e) ||
      (codePoint >= 0x2066 && codePoint <= 0x2069)
    ) {
      escaped += `\\u${codePoint.toString(16).padStart(4, '0')}`
    } else if ('\\`*_{}[]<>()#+-.!|'.includes(character)) {
      escaped += `\\${character}`
    } else {
      escaped += character
    }
  }
  return escaped
}

function validateRunUrl(value, repository) {
  assert.equal(typeof value, 'string', 'trusted workflow run URL must be a string')
  let url
  try {
    url = new URL(value)
  } catch (failure) {
    throw new Error('trusted workflow run URL is invalid', { cause: failure })
  }
  const expectedPath = new RegExp(
    `^/${repository.split('/').map(escapeRegularExpression).join('/')}/actions/runs/[0-9]+$`,
    'i',
  )
  assert.ok(
    url.protocol === 'https:' &&
      url.hostname === 'github.com' &&
      url.port === '' &&
      url.username === '' &&
      url.password === '' &&
      url.search === '' &&
      url.hash === '' &&
      expectedPath.test(url.pathname),
    'trusted workflow run URL is invalid',
  )
}

function validateRepository(value, path) {
  assert.equal(typeof value, 'string', `${path} must be a string`)
  const segments = value.split('/')
  assert.ok(
    segments.length === 2 && segments.every(segment =>
      REPOSITORY_SEGMENT_PATTERN.test(segment) && segment !== '.' && segment !== '..'),
    `${path} is invalid`,
  )
}

function validateSha(value, path) {
  assert.equal(typeof value, 'string', `${path} must be a string`)
  assert.match(value, SHA_PATTERN, `${path} must be a lowercase 40-character hexadecimal SHA`)
}

function validatePositiveInteger(value, path) {
  validateSafeInteger(value, path, 1)
}

function validateOptionalPositiveInteger(value, path) {
  if (value !== null) validatePositiveInteger(value, path)
}

function validateSafeInteger(value, path, minimum) {
  assert.ok(
    Number.isSafeInteger(value) && value >= minimum,
    `${path} must be a safe integer greater than or equal to ${minimum}`,
  )
}

function validateFiniteNumber(value, path) {
  assert.ok(typeof value === 'number' && Number.isFinite(value), `${path} must be finite`)
}

function validateOptionalFiniteNumber(value, path) {
  if (value !== null) validateFiniteNumber(value, path)
}

function validateBoundedString(value, limit, path) {
  assert.equal(typeof value, 'string', `${path} must be a string`)
  assert.ok(isWellFormedUnicode(value), `${path} must be well-formed UTF-8 text`)
  assert.ok(value.trim().length > 0, `${path} must not be blank`)
  assert.ok(Buffer.byteLength(value, 'utf8') <= limit, `${path} exceeds ${limit} UTF-8 bytes`)
}

function validateOptionalBoundedString(value, limit, path) {
  if (value !== null) validateBoundedString(value, limit, path)
}

function isWellFormedUnicode(value) {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index)
    if (code >= 0xd800 && code <= 0xdbff) {
      const next = value.charCodeAt(index + 1)
      if (!(next >= 0xdc00 && next <= 0xdfff)) return false
      index += 1
    } else if (code >= 0xdc00 && code <= 0xdfff) {
      return false
    }
  }
  return true
}

function exactKeys(value, expected, path) {
  assert.ok(
    value !== null && typeof value === 'object' && !Array.isArray(value),
    `${path} must be an object`,
  )
  assert.deepEqual(Object.keys(value).sort(), [...expected].sort(), `${path} has unexpected keys`)
}

function compareBlocks(left, right) {
  return left.round - right.round ||
    left.order - right.order ||
    left.revision.localeCompare(right.revision)
}

function arraysEqual(left, right) {
  return left.length === right.length && left.every((value, index) => value === right[index])
}

function escapeRegularExpression(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function deepFreeze(value) {
  Object.freeze(value)
  for (const child of Object.values(value)) {
    if (child !== null && typeof child === 'object' && !Object.isFrozen(child)) deepFreeze(child)
  }
  return value
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [artifactPath, repository, baseSha, headSha, runUrl, ...extra] = process.argv.slice(2)
  assert.ok(artifactPath && repository && baseSha && headSha && runUrl && extra.length === 0)
  const attributes = lstatSync(artifactPath)
  assert.ok(attributes.isFile() && !attributes.isSymbolicLink(), 'artifact must be a regular file')
  assert.ok(attributes.size <= MAX_ARTIFACT_BYTES, 'artifact exceeds five MiB')
  const artifact = parseArtifactBuffer(readFileSync(artifactPath), { repository, baseSha, headSha })
  process.stdout.write(renderComment(artifact, runUrl))
}
