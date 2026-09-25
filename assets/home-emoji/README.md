# ホームの3D絵文字

[Star-Struck Emoji - Star Eyes - Free Sample](https://www.cgtrader.com/free-3d-models/character/other/star-struck-emoji-star-eyes-free-sample)（作者: mtmediaofficial）を使用しています。

星形の目のベースカラーだけを赤（sRGB `#FF1D2D`）に変更しています。元の頂点・面・法線・材質の割り当てと、黄色い本体・口内の色を保持します。OBJ/MTLとGLBには元の光沢・粗さも保持します。形状の作り直し、球面への変形、平滑化、歯や舌の追加は行いません。

アプリ用にはモデル全体を平行移動・等倍スケールし、本体を半径1の球形の当たり判定に合わせます。実行時は従来のOpenGL ESレンダラーで位置・法線・頂点色を描画します。簡易照明のため、光沢や陰影はBlenderのレンダリングとは異なります。

## ローカルでの生成

CGTraderから自分のアカウントで無料の `StarEyes.obj` と `StarEyes.mtl` を取得し、プロジェクト直下に置いてください。Blender 5.2で以下を実行します。

```sh
blender --background --disable-autoexec --python assets/home-emoji/prepare_model.py
```

別の場所にある元ファイルを指定する場合と、確認画像を生成する場合:

```sh
blender --background --disable-autoexec --python assets/home-emoji/prepare_model.py -- \
  --source /path/to/StarEyes.obj --preview assets/home-emoji/preview.png
```

生成物:

- `star-struck-red-eyes.obj` / `.mtl`: 星の色だけを変更した編集用モデル。元の座標と形状を保持。
- `star-struck.glb`: 中心位置・スケールをアプリに合わせた編集用モデル。
- `app/src/main/assets/models/star-struck.mesh`: アプリ専用の描画データ。
- `preview.png`: 実際の3DモデルをBlenderでレンダリングした確認画像。

元のOBJ/MTLは上書きしません。

## ライセンスと公開ソース

このモデルは **CGTrader Royalty Free License (no AI)** です。アプリのコードやNotoのアプリアイコンとは別のライセンスです。

[CGTraderのライセンス説明](https://help.cgtrader.com/hc/en-us/articles/360015124437-Royalty-Free-License)では、組み込み製品としての利用と素材単体の再配布が区別されています。ソフトウェアにはモデルへのアクセスを防ぐ合理的な対策が求められています。

元ファイル・加工済みOBJ/MTL/GLB・アプリ用メッシュは、`.gitignore` とGitHub向けソースZIPの両方で除外しています。公開ソースからビルドする際は、上記の手順で素材を別途取得・生成してください。APKにはアプリ専用形式のメッシュを組み込みます。出典と加工内容は、アプリ内のライセンスダイアログとNOTICEにも記載しています。
