# 3Dモデルの準備

ホーム画面には、mtmediaofficialの[Star-Struck Emoji - Star Eyes - Free Sample](https://www.cgtrader.com/free-3d-models/character/other/star-struck-emoji-star-eyes-free-sample)を使用しています。

ライセンスは **CGTrader Royalty Free License (no AI)** です。元ファイルと加工済みモデルはこのリポジトリに含めていません。

## 手順

1. 配布元から `StarEyes.obj` と `StarEyes.mtl` を取得し、プロジェクト直下に置きます。
2. Blender 5.2で次のコマンドを実行します。

```sh
blender --background --disable-autoexec --python assets/home-emoji/prepare_model.py
```

アプリ用の `app/src/main/assets/models/star-struck.mesh` が生成されたら、アプリをビルドできます。変換により星形の目を赤に変更し、表示に合わせて位置とスケールを調整します。

モデルの利用には[配布元のライセンス](https://help.cgtrader.com/hc/en-us/articles/360015124437-Royalty-Free-License)が適用されます。元ファイル・加工済みのモデルデータを、ソースリポジトリに追加しないでください。
