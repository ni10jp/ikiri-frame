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

本体コードのMITライセンスはモデルやその描画画像には適用されません。モデルのライセンスは取得者に付与されるため、このリポジトリの取得だけではモデルの利用権を取得できません。

配布時は[CGTrader規約21A・21B](https://www.cgtrader.com/pages/terms-and-conditions)を確認してください。ソフトウェアへの組み込みには、モデルへのアクセスを防ぐ商業的に合理的な保護措置などの条件があります。本プロジェクトの独自メッシュ形式への変換だけで、その条件を満たすことを保証するものではありません。不明な場合は、実際の配布方法と形式について配布元に書面で確認してください。取得時のライセンスとダウンロード記録も保管してください。
