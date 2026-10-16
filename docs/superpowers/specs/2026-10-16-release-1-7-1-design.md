# Release 1.7.1 design

Fixes after the first test of 1.7.0, agreed with the user. The functional and technical specs
take each change in the commit that implements it; this note keeps the decisions and the reasons
in one place.

## Link chains

Every account that becomes active, at start and on each switch, makes a group read, and the
read's upkeep writes the missing direct links (`LinkUpkeep`). A friend's device so links the
friend's machines to a chain the user made at its next start, without a screen that reads the
group. The link groups stay as they are (see the 1.7.0 note).

## Plan form and account switch

The account menu writes what an open plan form holds back (`HeldWrites`) before it switches, so
the name typed last reaches the account the form was opened for. The form then closes as before.

## Tags in their own tables

Tags become rows: `tag` (`id`, `user_id`, `name`, `updated_at`, `deleted`) and `machine_tag`
(`id`, `user_id`, `machine_id`, `tag_id`, `updated_at`, `deleted`), a many-to-many link between
machines and tags. Both exist in SQLDelight and in `supabase/migrations/`, are synced as the
other tables are, and are read by group mates under the same rules as `machine_link`; a guardian
writes them for a managed child as it writes the child's machines.

- **Names are keys.** A tag name is stored trimmed, with inner spaces joined to one, in lower
  case (`tagKey`), on every write path. The spelling a tag was first typed in goes away; every
  screen still draws tags in capitals.
- **Ids follow from the content.** A tag's id is a UUID v5 of its owner and name, a
  `machine_tag`'s id a UUID v5 of its machine and tag name, in one fixed namespace. The same tag
  added on two devices offline, or converted on the server and on a device, is one row, so no
  reader meets two rows for one tag.
- **`Machine.tags` stays the domain view.** Repositories assemble it from the tables and write it
  through: a machine's upsert makes its tags' rows live and soft-deletes the `machine_tag` rows of
  the names it no longer has. A deleted machine loses its `machine_tag` rows.
- **An unused tag is deleted.** When no live machine of the owner holds a tag any more, the tag's
  row is soft-deleted in the same write. The machine form drops the chip of a tag that neither the
  edited machine nor another own machine holds, and the machine list and the picker drop a chosen
  tag that no machine has.
- **Older clients.** The user asks everyone to update; until then an older client may show no
  tags. `machine.tags` stays in both schemas only so that an older client's machine upsert still
  succeeds. Apart from the conversion, nothing writes or reads it, as with 1.0.2's `link_id`.
- **Conversion.** Migration `0027` converts every machine's `tags` once on the server.
  `19.sqm` adds the tables and resets `lastPullAt`; at start, before any write or sync pass, a
  device gives every local machine whose old `tags` column holds tags but which has no
  `machine_tag` row at all its rows from that column, and enqueues them. The ids match the
  server's, so a push of a converted row changes nothing.
