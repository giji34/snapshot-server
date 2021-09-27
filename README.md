# snapshot-server

- [giji34-plugin](https://github.com/giji34/plugins/tree/main/spigot) の `/gregenerate` コマンドを動かすためのユーティリティ

# components

## server

- `/gregenerate` コマンドを実行した時、指定された範囲のブロック情報を giji34-custom-server-plugin に提供するための Web サーバー

## core

- `server` が内部で使用するコマンドラインツール。リージョンファイル `r.*.*.mca`、または [gbackup](https://github.com/giji34/gbackup) のバックアップデータ `c.*.*.nbt.z` を読み取ってブロック情報を標準出力に JSON で出力する
