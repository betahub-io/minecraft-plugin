// Joins a local offline-mode Paper server, runs /bug and checks the dialog the server sends.
// It does not submit the dialog: with minecraft-data 3.119.0 the serverbound custom_click_action packet
// for 1.21.11 is decoded by Paper as client_command and the bot gets disconnected (library issue).
// Submission is covered by the MockBukkit tests and /bugreport test in the smoke test.
//
// Usage: node bot.mjs <port> [version]
import mineflayer from 'mineflayer'
import nbt from 'prismarine-nbt'

const port = Number(process.argv[2])
const version = process.argv[3] ?? '1.21.11'
const TIMEOUT_MS = 60_000
const EXPECTED_MAX_LENGTH = 1000

const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: 'E2EBot', version, auth: 'offline' })
const fail = (message) => { console.error(`FAIL  ${message}`); process.exit(1) }
const timer = setTimeout(() => fail('timed out waiting for the dialog'), TIMEOUT_MS)

bot.once('spawn', () => {
  console.log('pass  bot joined')
  setTimeout(() => bot.chat('/bug'), 1000)
})

bot._client.on('show_dialog', (packet) => {
  const data = packet.dialog?.data
  if (!data) fail(`dialog sent by registry reference, expected inline: ${JSON.stringify(packet.dialog)}`)
  const dialog = nbt.simplify(data)
  const inputs = dialog.inputs ?? []
  const text = JSON.stringify(dialog)

  expect(dialog.type === 'minecraft:confirmation', 'dialog received and is a confirmation dialog')
  expect(text.includes('Report a bug'), 'title from config')
  expect(inputs.map((input) => input.key).join(',') === 'description,steps', 'description and steps inputs')
  expect(inputs.every((input) => input.max_length === EXPECTED_MAX_LENGTH), `max length ${EXPECTED_MAX_LENGTH} on both inputs`)
  expect(inputs.every((input) => input.multiline !== undefined), 'both inputs are multiline')
  expect(String(dialog.yes?.action?.id).startsWith('paper:'), 'Submit button uses a Paper click callback')
  expect(dialog.no?.action === undefined, 'Cancel button only closes')

  clearTimeout(timer)
  bot.quit()
  setTimeout(() => process.exit(0), 500)
})

bot.on('kicked', (reason) => fail(`kicked: ${JSON.stringify(reason)}`))
bot.on('error', (error) => fail(`error: ${error.message}`))

function expect (condition, text) {
  if (!condition) fail(text)
  console.log(`pass  ${text}`)
}
