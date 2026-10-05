# ライセンスの適用範囲

このプロジェクトのオリジナルのアプリケーションコード、テスト、ビルド設定、スクリプト、文書は、[MIT License](LICENSE)（Copyright (c) 2026 ni10jp）で提供します。第三者に由来する部分には、それぞれのライセンスが優先して適用されます。MITによって第三者の権利を再許諾するものではありません。

この許諾は、公開済みのv1.4.60のオリジナルの本体コードにも適用します。

## 外部コードと素材

| 対象 | 適用条件・出典 |
| --- | --- |
| `app/src/main/java/jp/ni10/ikiriframe/color/` のJavaコード | GoogleのMaterial Color Utilities。Apache-2.0。[出典と変更内容](app/src/main/java/jp/ni10/ikiriframe/color/README.md) |
| AndroidX、Compose、Material Components、Kotlinなどの依存ライブラリ | 各配布元のライセンス。[クレジット](app/src/main/assets/licenses/NOTICE.txt) |
| `desugar_jdk_libs` | GPL-2.0 with Classpath Exception。例外によって独立した本体コードのMITライセンスを維持できますが、ライブラリ自身の条件は残ります。下記の対応ソースを参照してください。 |
| `desugar_jdk_libs_configuration`とR8の実行時補助コード | BSD-3-Clause。[著作権表示とライセンス](app/src/main/assets/licenses/R8-BSD-3-Clause.txt) |
| `gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar` | Gradle由来。Apache-2.0。元の著作権表示を保持しています。 |
| Mochiy Pop One、Google Sans Flexのフォントファイル | SIL Open Font License 1.1。[Mochiy Pop One](app/src/main/assets/licenses/MochiyPopOne-OFL.txt) / [Google Sans Flex](app/src/main/assets/licenses/GoogleSansFlex-OFL.txt)。ルートの`OFL.txt`もMochiy Pop One用です。 |
| Material Symbols / Material Icons由来の`ic_*.xml`（ランチャー用を除く） | Apache-2.0。GoogleのアイコンをAndroid VectorDrawableへ変換しています。 |
| カラーアプリアイコンの絵文字部分 | Google Noto 3D Emoji由来。上流READMEの画像向けApache-2.0表記と、ルートLICENSEのOFL-1.1表記に不一致があります。両ライセンスと出典を保持し、単一のライセンスとは断定していません。[クレジットと出典](app/src/main/assets/licenses/NOTICE.txt) |
| モノクロアプリアイコン | OFL-1.1のNoto Emojiフォントから作成したアウトライン。[フォントのライセンス](app/src/main/assets/licenses/NotoEmoji-OFL.txt)。OFLの文書・図形への出力に関する規定は、フォントそのものの再配布とは異なります。 |
| ホーム画面の3Dモデル、モデルを描画した`assets/home-emoji/preview.png` | mtmediaofficialのStar-Struck Emoji。CGTrader Royalty Free License (no AI)。モデルデータは公開ソースに含まれません。[モデルの準備と利用条件](assets/home-emoji/README.md) |

本体コードへのMIT許諾には、アプリアイコン、画像、フォント、3Dモデルその他の美術素材を含めません。これらの再利用は上記の出典と適用条件を個別に確認してください。フォントのアウトライン化やモデルの色変更によって、元の権利が消えるわけではありません。

第三者の著作権表示、ライセンス本文、出典と変更内容は[`app/src/main/assets/licenses/`](app/src/main/assets/licenses/)に収録しています。このディレクトリはAPKにも同梱されます。MITによる本体コードの許諾は、第三者の商標・肖像等の使用許諾を含みません。

公開済みのv1.4.60 APKには、今回補足したMIT・BSDの表示は入っていません。[同じRelease](https://github.com/ni10jp/ikiri-frame/releases/tag/v1.4.60)の`ikiri-frame-1.4.60-licenses.zip`で補足する著作権・ライセンス表示を提供します。APKの再配布時には、この案内も添付してください。

## desugar_jdk_libsの対応ソース

v1.4.60は`com.android.tools:desugar_jdk_libs:2.1.5`を使用しています。上流ソースのリビジョンは[`73170c345e6a762fc6a1f0301bb15218850023ef`](https://github.com/google/desugar_jdk_libs/tree/73170c345e6a762fc6a1f0301bb15218850023ef)です。ライブラリのソースに独自の変更は加えておらず、Androidの標準ツールで変換しています。

対応ソース、上流のビルドスクリプト、ライセンスとビルド条件をまとめた`ikiri-frame-1.4.60-desugar-source.zip`を、[APKと同じRelease](https://github.com/ni10jp/ikiri-frame/releases/tag/v1.4.60)で提供します。APKを別サイトで再配布する場合も、このソース一式とライセンス案内をAPKと一緒に提供してください。アプリ本体のコードをGPLに変更するという意味ではありません。
