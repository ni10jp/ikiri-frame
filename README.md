# ｲｷﾘﾌﾚｰﾑ

Pixel向けの、写真の下にExif入りウォーターマークを追加するAndroidアプリ。Kotlin / Jetpack Composeで実装しています。対応OSは **Android 7.0（API 24）以降**です。

バージョンは1.4.60です。ビルド済みAPK、開発環境の設定、検証ログ・スクリーンショットはソースリポジトリに含めません。

APKの直接配布を想定しています。配布用APKにはリリース署名が必要です。

## 使い方

1. ホームの「イキる写真を選ぶ」からAndroid標準の写真選択を開きます。Photo Picker未対応端末ではAndroidXがシステムのファイル選択へ切り替えます。
2. 編集画面でロゴとバーの太さを選び、「ラベル」行をタップしてExifの表示値と「右のやつ」を変えます。「カラー」の横スクロールから写真のバーに使うパレットを選べます。「Android」は端末の配色です。
3. 編集の「ロゴ」行をタップしてロゴ選択画面を開きます。同じ画面でSVG・透過PNGの登録とスワイプ削除ができ、「なし」も選べます。
4. 「書き出し」でJPEGを保存します。Android 10以降は `Pictures/IkiriFrame`、Android 7〜9はOS標準の保存先選択画面（CreateDocument）を使います。

写真アプリなどの **共有 → ｲｷﾘﾌﾚｰﾑ** からも、画像1枚を直接取り込んで編集できます。共有の一時的な読み取り権限がある間にアプリ専用ストレージへコピーするため、元アプリを閉じても編集を続けられます。アプリ起動中の新しい共有にも対応し、読込・書き出し中なら完了後に最新の共有画像を取り込みます。複数枚の一括共有には対応していません。

Android 12以降は壁紙のダイナミックカラー、Android 7〜11はM3標準のライト／ダーク配色を使います。写真から抽出するテーマと固定パレットは全対応OSで利用できます。Android 7/8の写真読込・Exifの回転と反転はCoil 3.4.0に任せ、日時APIは標準のcore-library desugaringで互換化しています。可変フォントの軸はAndroid 8以降、細かなフォントの太さはAndroid 9以降で反映し、旧OSでは通常のフォントスタイルを使います。OSから画面の角丸を取得できないAndroid 11以前では画面の矩形を当たり判定に使います。振動プリミティブ非対応OSでは、操作完了・衝突時をAndroidXの標準触覚で処理し、粘着中の振動は行いません。

元の写真は変更しません。広範囲な写真アクセス権限・ストレージ権限・ネット接続は不要です。フォントと画像アセットはアプリに同梱しています。

## ビルド

アプリのパッケージ名（applicationId / namespace）は `jp.ni10.ikiriframe` です。

Android Studioでこのフォルダーを開き、SDK ManagerからAndroid SDK Platform 37をインストールしてください。JDK 17以降が必要です。Gradle Wrapperは同梱しています。

公開ソースにはホームの3Dモデルデータを含めていません。最初に[素材の取得・変換手順](assets/home-emoji/README.md)を実行してください。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

デバッグAPKは `~/.gradle/ikiri-frame-builds/<プロジェクトパスのID>/app/outputs/apk/debug/app-debug.apk` に生成されます（Gradle User Homeを変更している場合はその配下）。最後のコマンドにはAPI 24以降の端末またはエミュレーターが必要です。リリースビルドは `:app:assembleRelease` で生成でき、配布用の署名はAndroid Studioの「Generate Signed App Bundle / APK」から設定できます。

Android StudioからのビルドもGradle User Home配下を使います。IDEのデプロイ設定によってAPKが `app/intermediates/apk/debug/` に切り替わる場合は、AGPのAPK出力情報に従います。ソースのフォルダー内にコンパイル生成物を置かず、同期による `EditorState 2.class` などの重複がビルドに混入するのを防ぎます。Studioが旧ビルド先を記憶している間も起動できるよう、旧パスの `redirect.txt` は各assembleタスクで実際のAPK出力先へ自動更新します。設定変更後は一度「Sync Project with Gradle Files」を実行してください。IDEに `InvalidVirtualFileAccessException` が出て同期に失敗する場合は、プロジェクトを閉じて開き直してください。出力先は従来どおり上書きできます。

```sh
./gradlew -PikiriBuildRoot=/tmp/ikiri-build \
  --project-cache-dir /tmp/ikiri-gradle-project :app:assembleDebug
```

この場合のAPKは `/tmp/ikiri-build/app/outputs/apk/debug/app-debug.apk` です。

## 実装した仕様

- ホーム・編集・ロゴ選択の3画面。標準Small top app barと戻るボタン。編集画面だけApp barの通常時・スクロール時の塗りつぶしを透明にし、サイドシートの影を上から覆わないようにします。ホームとロゴ選択の塗りつぶしは保持します。
- ホームのDisplay Largeは、提供されたMochiy Pop One Regularを使用。
- M3 ExpressiveのLarge Filledボタン。`ButtonDefaults.shapesFor` による標準の押下時シェイプ変形。
- 編集のプレビューは、App bar・ボトムシートまたはサイドシート・画面端の間にある空き領域へ初期表示をFitします。プレビュー周囲の固定余白と高さ上限は設けません。[Telephoto 0.19.0](https://saket.github.io/telephoto/zoomable/)の `Modifier.zoomable` でピンチ拡大縮小・移動・ダブルタップ拡大に対応します。`clipToBounds = false` とし、プレビューと親の領域でもクリップしないことで、拡大中はApp barやシートの下・システムバーの領域まで描画します。App barとシートは標準の描画順で写真の前面に重なります。半開きの下側シートも外側の背景は透明にし、シート自体の角丸と塗りつぶしだけを重ねます。写真とバー全体の描画領域をライブラリへ渡し、`ZoomSpec(maxZoomFactor = 8f)`でズーム上限を設定します。ジェスチャー・移動範囲・上限の制御・アニメーションはライブラリの標準処理に任せます。表示の拡大率は書き出す画像に影響しません。
- 縦長ウィンドウは標準BottomSheetScaffold、横長ウィンドウは非モーダルの右側シート。書き出しボタンはプロパティの最後に並び、項目と同じ領域でスクロール。キーボードに追従する固定ボタンは置きません。
- 折り畳み端末は[Material 3 AdaptiveのcollectFoldingFeaturesAsState](https://developer.android.com/develop/adaptive-apps/guides/foldables/make-your-app-fold-aware)で現在のウィンドウの折り目を取得します。画面を分離する水平の折り目では上側にプレビュー・下側にBottomSheetScaffold、垂直の折り目では左右にプレビューとサイドシートを配置します。Compose標準のColumn／RowとSpacerで実際の折り目の領域を空け、App bar下のコンテンツ座標に合わせます。シートには残りの領域をweightで渡します。Gridのfrトラックによるintrinsic計測はBoxWithConstraintsやシートと互換性がないため使用しません。半開きと物理ヒンジで分離された画面が対象で、通常のフラット画面では従来の配置に戻ります。プレビューは片側の領域全体を使用し、操作欄はその領域内でスクロールできます。Compose標準のSaveableStateHolderで配置をまたいだズーム・スクロール状態を保持します。
- アプリの画面・ホームとロゴ選択のApp bar・ダイアログはsurfaceContainer。編集画面のApp barは透明です。プロパティのボトムシートとサイドシートはsurfaceContainerLowとし、標準のshadowElevationを12dpに設定します。半開きの配置にも同じ設定を適用します。編集ScaffoldのcontentWindowInsetsを空にして、シートの背景は下端・左右端のシステムバー領域まで描画します。操作欄とプレビューの初期Fit領域は、Compose標準の[recalculateWindowInsets](https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/recalculateWindowInsets.modifier)とsafeDrawingPaddingで、それぞれの配置に必要な余白だけを内側に確保します。操作欄の余白はverticalScrollの内側に置き、システムバー手前で内容を切り取る追加のclipToBoundsは設けません。スクロール中はシート端まで描画し、末尾では書き出しボタンの下にシステム余白を確保します。通常のボトムシートは下側のシステム余白を含む高さにして、操作領域の高さを維持します。未選択のリスト項目と通常時の「新規」行はsurfaceBright。設定項目の外側の角はlargeIncreased、接する角はextraSmall。内側16dp / 10dp、項目間2dp。
- 「カラー」は他のプロパティと同じsurfaceBrightのコンテナにタイトルと横スクロールのパレットを表示します。タイトルの左右余白は16dp、パレットの左右余白はLazyRow標準のcontentPaddingで16dpとし、横スクロールの描画領域はコンテナの端まで広げます。太さはSmallの標準M3スライダー（16dpトラック）。
- 書き出しアイコンはMaterial Symbols Roundedのupload、weight 600。
- UIはダイナミックカラーとダークモードに対応。写真バーのライト／ダークは「ダークテーマ」で選択し、プレビューと書き出しに適用。
- 各画面をそれぞれActivityで表示します。編集画面のApp barの戻るボタンとAndroidX標準BackHandlerから、本文なしの「破棄しますか？」ダイアログを表示します。「破棄」で編集中の処理をキャンセルし、編集セッションを消してActivityを終了します。「編集に戻る」・ダイアログ外のタップ・ダイアログの戻る操作では編集状態を保持します。確認の表示状態はrememberSaveableで保持します。写真選択から開いた編集画面はホームへ、他アプリの共有から開いた場合はその共有元へ戻ります。ラベル入力中の戻る操作は先に入力ダイアログが処理します。
- 写真、設定、ラベルの未保存の入力を端末内に保存し、Androidホームや他アプリからの復帰、画面回転、プロセス再生成時に復元。ランチャーから戻る画面はAndroid標準のタスク・Activity管理に任せます。ホームから保存済みの写真を探して編集画面を自動起動する処理はありません。写真はOSが削除できるキャッシュに置かず、元の共有URIの権限にも依存しません。
- 選択ロゴ、太さ、テーマ抽出のオン／オフ、固定カラーパレット、写真バーのライト／ダークは次の写真にも引き継ぎます。Exifの表示値と右のラベルは写真ごとに保持し、新しい写真を読み込むと表示値を読み直して右のラベルを空欄にします。編集中の写真を復元するときは保存済みの入力を保持します。

ラベル編集は[公式ドキュメントのフォーム用構成](https://developer.android.com/develop/ui/compose/components/dialog#dialog-composable)に従い、Compose標準の `Dialog` 内にM3の内容を表示します。ウィンドウ生成・入力用View・計測・IMEへの接続はComposeの実装に任せ、アプリ独自の `ComponentDialog` / `ComposeView` は使いません。DialogPropertiesは既定値を使います。プロパティシートの外に、スクロールする内容を1つだけ置き、高さは `heightIn(max = 480.dp)` で上限を付けます。タイトル・入力欄・二段のボタンをまとめてスクロールでき、見出しやボタン用の固定領域はありません。IME余白・キーボードの高さ計算・独自フォーカス解除・キーボードの開閉処理は追加しません。ラベルの戻る操作はCompose標準の `onDismissRequest` に従います。現在のCompose Dialogはライブラリ内部で戻るを処理するため、OSが進行度を制御するダイアログの予測型戻るアニメーションは使われません。入力欄のないライセンスと破棄確認は `ComponentDialog` を使い、OSの予測型戻る・キャンセル・閉じるアニメーションに任せます。

## ラベルと登録ロゴ

「ラベル」行をタップして機種・焦点距離・F値・シャッタースピード・ISO値・撮影日時と「右のやつ」を変更できます。入力欄はCompose標準のTextFieldStateで文字・選択範囲・変換途中の状態を保持し、文字列のonValueChange経由で入力を往復させません。120文字制限はInputTransformation.maxLength、単一行はTextFieldLineLimits.SingleLineを使用します。IMEの「次へ」「完了」は標準動作に任せ、独自のキーボード開閉やフォーカス移動は行いません。下書きは入力状態から一方向に保存し、保存ボタンは入力欄の現在値を直接読み取ります。「右のやつ」は写真バー右端の文字列（初期値は空欄）です。単位も含めた表示文字列として編集し、空欄の項目は表示しません。ダイアログ内の「リセット」で読み込んだ値に戻し、「保存」で反映します。「キャンセル」や戻る操作では編集中の値を破棄します。ボタンはM3標準のシェイプモーフィング付きです。上段の「リセット」は全幅のOutlinedButton、下段の「キャンセル」はerror / onErrorのFilled、「保存」は通常のFilledで、等幅で行全体を埋めます。ライセンスの「閉じる」は全幅のerror / onErrorのFilledです。破棄確認は「破棄」をerror / onErrorのFilled、「編集に戻る」を通常のFilledで等幅に並べます。編集内容はバーの表示に使い、元画像やJPEG内部に保持する撮影Exifは書き換えません。「右のやつ」も自由入力で、空欄なら非表示です。どちらも「保存」まで写真バーには反映せず、「キャンセル」で下書きを破棄します。「リセット」はExifを読み込んだ値に、「右のやつ」を空欄に戻す下書き操作です。

編集の「ロゴ」項目からロゴ選択画面を開きます。ホームのApp barにはロゴへの入口を置きません。追加専用画面はなく、リスト末尾のMaterial Symbols Rounded・weight 600のaddアイコン付き「新規」項目からAndroid標準のファイル選択を使います。全項目は高さ72dp、ロゴのプレビューはファイル名の左に表示します。「新規」はリストと同じ配置のM3 Buttonで、押している間だけ全ての角がLarge-increasedになります。背景はsurfaceBright、アイコンとラベルはonSurfaceVariantで、押下中もこの配色を保ちます。角の変化はButtonDefaults.shapesと標準の押下状態によるシェイプモーフィングを使用し、離す・キャンセルする操作で通常状態へ戻ります。説明文や独立した登録ボタンは置きません。リスト末尾の下に96dpのスクロール可能な余白を設け、下部にメッセージが出ても最後の項目を上へ動かして操作できます。ライセンスはホームのApp bar右上の情報アイコンから開きます。

登録ロゴは左右どちらのスワイプでも削除できます。「なし」と「新規」は削除対象に含めず、選択中のロゴを削除すると「なし」に戻ります。使用中のロゴをスワイプしている間は、画面外へ向かう移動量に応じて「なし」の背景・文字色・角丸・文字の太さ・選択マークが徐々に選択状態へ変化します。表示だけを先に変え、保存される選択は削除成功時に切り替えます。スワイプを戻した場合や削除に失敗した場合はハイライトを戻し、未選択のロゴを削除するときは選択を変えません。スワイプは、最初の120dpの指の移動に対して行を32%だけ動かし、接している行の角をExtra-smallからMediumへ広げます。粘着中は上下3行が、対象行の移動距離の1/8・1/16・1/32ずつ追従します。周囲の接する角丸も行同士のずれに連動し、解除時はFast Spatialで周囲の行が元へ戻ります。境目を越えると強いクリックの触覚とともにMediumからLarge-increasedへExpressive Fast Effectsで変形し、指を離すと画面の端まで飛び出して削除します。この移動はM3のSwipeToDismissBoxと同じ `AnchoredDraggableDefaults.SnapAnimationSpec` を使用し、独自の最低初速・移動時間・速度計測は設定しません。粘着と周囲の追従はカスタムのジェスチャー処理です。各スワイプで最初に動かした方向の空いた側（左へ引き始めたら右、右へ引き始めたら左）だけに、粘着中から高さ72dpのerror色のピルとonError色の削除アイコン（Material Symbols Roundedのdelete、weight 600）を表示します。幅が高さ未満でもアイコンを非表示にせず、24dp未満の幅では収まる大きさで描画します。ピルと行の間に余白を置かず、動いている行の端に追従させます。ピルの側は操作中に切り替えず、戻るオーバーシュートで反対側へ動いた間は表示しません。同じ操作中に最初のスワイプ方向の反対側へ引いた場合は、距離にかかわらず32%の粘着抵抗を保ち、粘着解除・解除時の触覚・削除は発生しません。「なし」のハイライトも最初の方向への移動中だけ進みます。反対側で指を離すと元へ戻り、戻り終わってから始める次のスワイプでは方向を選び直せます。元の位置へ戻り終わるか画面を離れて操作が中断された時点で方向をリセットし、次のスワイプ開始時にも初期化します。行が画面外へ出たあと、M3標準のExpressive Default Spatial（defaultSpatialSpec）で隙間を閉じ、角丸も同じ進行度でExtra-smallに戻します。リストの外側の角はLarge-increasedを保ちます。オーバーシュートは接する端だけを最大8dpクロップして吸収し、角丸はクロップ後の輪郭に描き直します。行と中身は72dpのまま測定し、拡縮しません。解除後も元の位置から12dp以内まで戻すと再び粘着し、もう一度引くと抵抗と解除の触覚が働きます。戻す操作やキャンセルでは削除せず、標準のExpressive Fast Spatialで元の位置を少し越えてから収束します。周囲の追従は戻る動きに連動します。粘着を剥がした後に戻すときも、接する角丸はM3標準のfastEffectsSpecで現在の形から滑らかに戻します。途中で引き直した場合は、その時点の角丸から新しい形へ補間します。

粘着中の触覚は[Android公式のResist例](https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects#resist)を参考に、標準LOW_TICKを短い間隔で再生し、引く量に応じて強度を滑らかに上げます。端末が返すプリミティブの長さを考慮して途中で打ち切らず、独自の振動波形は使いません。解除時には抵抗の再生を止めて[HEAVY_CLICK](https://developer.android.com/reference/android/os/VibrationEffect#EFFECT_HEAVY_CLICK)を一度だけ鳴らし、飛び出しと隙間を閉じるアニメーションには触覚を追加しません。指を離す・戻し切る・操作をキャンセルする・画面を離れる場合も止めます。LOW_TICK非対応端末では抵抗の効果を省略し、システムの触覚設定・画面のフォーカスに従います。

SVG、または透過部分を含むPNGに対応します（8MB以下、PNGは400万画素以下）。SVGには幅・高さまたはviewBoxが必要です。SVGのルートの描画サイズを記録領域に合わせ、元のviewBoxの原点・縦横比の指定を保持します。viewBoxがない場合は元の幅・高さを座標系として補い、pt・mmなどの単位変換はAndroidSVGに任せます。小数の描画領域も保持し、プレビュー・書き出しとも同じ領域全体をロゴ枠へ拡縮します。登録済みSVGも読み込み時に適用されるため、再登録は不要です。登録時にアプリ内へコピーするため、元ファイルを移動しても利用できます。削除は登録コピーだけが対象です。削除時は保持している一覧から対象だけを除き、残りのロゴを再読込・再描画用にデコードし直しません。画面全体の読込状態に切り替えず、一覧から削除が反映されるまで対象行の閉じた状態を維持します。削除が失敗した場合は行を戻して操作を再開します。Googleロゴは同梱していません。

プロパティは「ロゴ」「ラベル」「太さ」「カラー」「ダークテーマ」の順に並び、各項目の最小高さは72dpです。編集の「ロゴ」「ラベル」はM3標準のクリック可能なListItemで、右端に矢印アイコンを表示し、行全体をタップして選択画面・編集ダイアログを開きます。クリック・リップル・操作の無効化はListItem標準の処理を使います。プロパティのクリック可能な行は、ListItemShapesの全状態に同じ角丸を渡し、押下・フォーカス時も背景の形を変えません。行のシェイプモーフィングはロゴ選択画面だけに適用します。通常のプロパティには選択ロゴ名や小さなプレビューを並べません。ロゴ選択画面は「なし」と登録ロゴ、その末尾の「新規」を、プロパティと同じ角丸のリストに表示します。項目は高さ72dp固定で、ラジオボタンは表示しません。選択中の行の右端にMaterial Symbols Roundedのcheck_circle（FILL 1、weight 600、24dp）を表示します。選択中の項目は「なし」を含め、全ての角をLarge-increased、背景をprimaryContainer、ラベルをonPrimaryContainerにします。選択中の角はスワイプ中も維持し、隙間を閉じる際の端のクロップは引き続き適用します。「なし」と登録ロゴの行にもM3 Buttonの標準シェイプモーフィングを使い、未選択の行は押している間に全ての角がLarge-increasedになります。選択済みの行は、押している間だけ行の高さに対応するM3標準の押下形状に変わり、離すと全角Large-increasedに戻ります。単一選択のアクセシビリティと削除操作の触覚は保持します。ロゴのプレビュー、拡張子付きファイル名の順に左から並べます。選択中のラベルは通常のweightに200を加え、選択・解除時の角丸と文字の太さを同じ進行度のExpressive Default Effectsで変化させます。選択はその場で保存され、戻る操作で元の画面へ戻ります。以前のバージョンが拡張子を削除して保存した登録名には、保存形式から `.svg` または `.png` を補います。新しく登録したファイル名は元の大文字・小文字も含めて保持します。

書き出すロゴの高さを28に揃え、横幅は比率どおりに確保します。文字やロゴが幅を超える場合は、全体を縮小して重なりを防ぎます。

ロゴの初回読込には高さ72dpの行型シマーを表示し、登録中は既存の一覧を保持したまま「新規」行の内容をシマーに置き換えます。App bar直下に読み込みバーは表示しません。編集画面を開く際の写真の読み込みも、円形インジケーターからプレビュー領域のシマーに統一します。読み込み状態はアクセシビリティにも伝えます。シマーは[Compose Shimmer](https://github.com/valentinilk/compose-shimmer)の標準アニメーションを使用します。「カラー」のパレット一覧も読込中は同じシマーを使用します。72dp・Mediumのタイル、8dp下のラベル、12dpの横間隔を保ったプレースホルダーを表示し、読み込みが終わってから保存済みの設定で選択状態を直接描画します。未読込のパレットを「Android」の選択として扱わず、復元時のシェイプ・色変更アニメーションや触覚も発生させません。写真プレビューの色アニメーションも写真ごとに初期化し、読み込み直後のバーがシステム色から選択色へ変わる動きを防ぎます。読込に失敗した場合はシマーを停止します。

ラベルダイアログの入力欄は標準のOutlinedTextFieldを使い、移動・枠線・色・透明度のアニメーションをExpressive Fast Spatialに揃えます。入力欄だけにMaterialThemeのmotionSchemeを適用し、キーボード・フォーカス制御は標準処理を保ちます。

## ホームの3D絵文字

U+1F929（🤩）の3Dモデルがホームの文字やボタンの上を転がります。Android標準のGame Rotation Vector（ジャイロ＋加速度の融合）で端末の傾きを読み、慣性と回転を伴って移動します。傾きに対する加速を強めて転がる速度を上げ、減衰は初期の値へ戻しています。縦・横向きに対応し、画面の四辺と、Androidの `WindowInsets.getRoundedCorner` が返す四隅の円弧で跳ね返ります。端末の角の中心・半径を画面上の座標に変換し、球体の半径を含めて当たり判定します。角丸情報がない画面では四辺を使用します。モデルと影はステータスバー・ナビゲーションバーの領域にも描画し、球体が画面端に接する位置で衝突します。センサーが使えない場合は静止表示します。傾きは各フレームで現在の画面回転に合わせて変換し、90度・270度の横向きでも画面の低い側へ転がります。画面だけが回転してActivityが継続する場合も、移動速度の向きを合わせます。

転がっている最中の触覚フィードバックはありません。壁や角に衝突したときだけ、Android標準の `PRIMITIVE_THUD` を衝突速度に応じた強さで再生します。波形は自作せず、非対応の端末では省略します。ホームを離れたら再生をキャンセルします。`VIBRATE` 権限と `USAGE_MEDIA` を使用し、システムの触覚無効設定にも従います。

モデルはOpenGL ESで実際のメッシュを描画し、柔らかな接地影を付けています。衝突時は衝突方向につぶれ、体積を保つように直交方向へ少しふくらみます。変形量は衝突速度に応じ、圧縮の上限は10%です。復元はAndroidXのFloatSpringSpec（MediumBouncy / StiffnessMedium）を使い、小さく弾んで元の形に戻ります。モデル・法線・影に同じ変形を適用し、球形の当たり判定や運動は変えません。停止後も復元が終わるまで描画し、画面回転では変形方向も追従し、ホームを離れると変形をリセットします。タップ・ドラッグ操作はなく、モデルの下にあるボタンをそのまま押せます。ホームを離れるとセンサーとフレーム更新を停止し、静止時はGPUの再描画を止めます。遊び方の説明ラベルは追加していません。

モデルはmtmediaofficialの[Star-Struck Emoji - Star Eyes - Free Sample](https://www.cgtrader.com/free-3d-models/character/other/star-struck-emoji-star-eyes-free-sample)です。星の目だけ赤に変更し、元の形状・法線・本体と口の色を保持しています。配置と等倍スケールを調整し、球形の当たり判定を使います。ライセンスはCGTrader Royalty Free License (no AI)で、素材単体の再配布はできません。元ファイルと加工済みのモデルデータは公開ソースから除外しています。[素材の取得・変換手順](assets/home-emoji/README.md)に従って、ローカルでアプリ用メッシュを生成してください。ホームのApp barの情報アイコンから出典と加工内容を確認できます。

## 写真からテーマを抽出

「カラー」は保存済みの設定を引き継ぎ、初回のみ「Android」で端末のダイナミックカラーを使用します。写真から抽出したパレットも同じ一覧から直接選べます。以前のバージョンの抽出設定と編集中のパレットは引き継ぎます。反映先はプレビューと書き出し画像のバーだけです。編集画面を含め、アプリUIは常に端末のダイナミックカラーを使用します。

Google公式のMaterial Color Utilitiesを同梱し、写真の読込時に長辺128px以下のサンプルからバックグラウンドで抽出します。Celebi量子化とScoreで最大3色を選び、代表色からContent・Tonal Spot・Vibrant・Expressive・Neutral、ほかの候補色からTonal Spotの配色を生成します。2021仕様、標準コントラストを使い、ライト／ダークの両配色を保持します。低彩度の写真は写真内の代表色、完全に透明な写真は端末色にフォールバックします。色抽出は端末内で完結します。

「カラー」のコンテナ内に、先頭の「Android」、写真由来のパレット、Monochrome、Red・Orange・Yellow・Lime・Green・Mint・Cyan・Azure・Blue・Purple・Magenta・Pinkの順に、72dp・Medium、12dp間隔で横スクロール表示します。MonochromeはMCUのSchemeMonochrome、固定12色は30度刻みのRGB色相を元にしたSchemeTonalSpotを使います。未選択時は上半分がPrimary、左下がSecondary Container、右下がTertiary Fixed Dim。下半分は境界に隙間ができない1枚の矩形として描画します。タイルの色とアイコンをCompose標準のCompositingStrategy.Offscreenで先に合成し、M3ボタン側の輪郭でまとめて切り抜きます。角丸の縁に下地が混ざる重ね描きを避け、押下・選択のシェイプ変形は保持します。選択するとAndroidX MorphでM3のCookie12Sidedへ変形し、全面Primaryに32dpのチェック（Material Symbols Rounded、FILL 1、weight 600、onPrimary）を表示します。未選択時の押下はM3 Buttonの標準シェイプ変形です。

英語の名前は8dp下に、Google Sans FlexのtitleSmallで表示します。名前は1行で表示し、収まらない部分は末尾を「…」で省略します。slnt 0・wdth 72を保ち、選択の変形と同じ進行度でweight 200→600、ROND 0→100へ変わります。文字色はアプリのonSurfaceです。選択は編集状態とともに保存します。新しい写真では端末色を使う設定ならAndroid、写真色を使う設定ならContentになります。固定パレットを選んだ場合はその選択を引き継ぎます。パレット操作では再抽出せず、写真の取込・復元時に候補を生成します。

選択時の12面クッキーへの変形・名前の太さ・タイルの配色・写真バーの配色は、M3 ExpressiveのfastEffectsSpecで滑らかに変わります。[Compose標準のTransition](https://developer.android.com/develop/ui/compose/animation/value-based#transition)を使い、切替途中の再操作も現在値からつなぎます。書き出しはアニメーション途中の色を使わず、選択先の配色を使います。

「カラー」の次に高さ72dp以上の「ダークテーマ」行を置きます。[M3標準のアイコン付きSwitch](https://developer.android.com/develop/ui/compose/components/switch#custom-thumb)を使い、thumbContentにオンではチェック、オフでは×をSwitchDefaults.IconSizeで表示します。アイコン・つまみ・トラック・無効時の色とアニメーションはM3の既定値を使い、独自の色指定は行いません。行全体はクリック可能なM3 ListItemで切り替え、リップルと角丸のクリップはListItem内部の標準処理に任せます。アクセシビリティにはスイッチの役割とオン／オフ状態を提供します。オン／オフには標準ToggleOn／ToggleOffの触覚を付けます。初期値はオフで、この選択は写真バーのプレビューと書き出しに適用します。カラーテーマ一覧のタイルはAndroid・写真由来・固定パレットともCompose標準のisSystemInDarkTheme()に従い、写真バー用のトグルには連動しません。アプリ自体のライト／ダークは引き続きOS設定に従います。編集状態と設定に保存し、画面の再生成や別アプリからの復帰でも保持します。

## 触覚フィードバック

M3 Expressiveのコントロール操作からComposeの標準`LocalHapticFeedback`を呼び、Androidが端末に合う振動を再生します。通常のボタンは標準の触覚を単独で使います。ラベルのリセットだけは指定の短いバイブとして35msの一回振動を使い、強度調整に対応する端末では100/255とします。Android 7にも同じ長さのフォールバックを用意し、端末の触覚設定に従います。無効なコントロールや設定の復元では振動しません。

| 操作 | Android標準の種類 |
| --- | --- |
| イキる写真を選ぶ | `VirtualKey`（押下） |
| 書き出し | `Confirm`（操作の確定） |
| 太さの値変更 | 最小65%・最大175%に到達した時だけ `GestureThresholdActivate`。途中の移動・端での停止・値の復元では鳴らしません。 |
| カラーのパレットを選択 | OS標準の [`PRIMITIVE_SPIN`](https://developer.android.com/reference/android/os/VibrationEffect.Composition#PRIMITIVE_SPIN) を強度0.25で1回だけ再生。公式の弾性表現で使われる、振幅と周波数が往復する効果を使用します。独自波形や複数効果の組み合わせは使いません。非対応端末では標準 `CLOCK_TICK` に切り替えます。 |
| プロパティのロゴ・ラベルをタップ | `VirtualKey`（押下） |
| ロゴ選択の項目をタップ | `GestureEnd`（操作完了） |
| ロゴの「新規」をタップ | `VirtualKey`（押下） |
| ラベル編集のリセット | カスタムの短いバイブ（35ms） |
| ラベル編集のキャンセル | `Reject` |
| ラベル編集の保存 | `Confirm`（確定） |
| ライセンスの「閉じる」・編集の「破棄」 | `Reject` |
| 破棄確認の「編集に戻る」 | `VirtualKey`（押下） |

## ウォーターマーク

画像の下に新しくバーを追加します。画像の長辺ではなく **幅720のデザイン座標** を基準とし、元の解像度に比例して描画します。画面密度やユーザーの文字サイズによって保存画像は変わりません。プレビュー・書き出しは同じ `FrameRenderer` を使用します。

| 要素 | 設定 |
| --- | --- |
| バー | 左右24、上下12。基本高さ62 |
| ロゴ | 高さ28、幅は元の縦横比に従う。左12、右12 |
| 区切り | 幅1、内側の高さ38、右余白8 |
| 機種名 | Google Sans Flex、14 / 20、weight 600、opsz 14 |
| Exif下段 | 実焦点距離・F値・シャッター・ISO・撮影日時。12 / 16、weight 400、opsz 12 |
| Exif共通 | slnt 0、wdth 100、GRAD 0、ROND 100。行間の隙間2 |
| 右のラベル（初期値は空欄） | Google Sans Flex Regular、22 / 28。標準軸（ROND 0、opsz 18） |
| 配色 | surfaceContainerLow / onSurface / onSurfaceVariant / outline |

太さは基準の65〜175%。文字が重ならない範囲で内容も比例して拡大縮小し、長いExifでは内容を幅に収めます。ロゴに「なし」を選ぶと区切り線とその余白も消えます。Exifがない項目は空欄にし、代替の文言・記号・単位・区切りを表示しません。右のラベルも初期値は空欄です。撮影時刻はExifに記録されたローカル時刻で、初期値とリセット時は秒を切り捨てたyyyy/MM/dd HH:mm形式で表示します。手入力した日時はそのまま使用し、元の撮影Exifの秒数は保持します。

プレビュー・書き出し共通でバー色を下地に描き、透過写真の範囲だけ白で埋めます。写真の外周にアンチエイリアスを重ねず、拡縮時に画像とバーの接点へ白地が混ざるのを防ぎます。

保存はJPEG品質98、sRGB。EXIFの回転・反転はImageDecoderまたはCoilによるデコード時に適用してから描画し、出力の向きは正常に設定します。機種・撮影日時・露出などの撮影情報を保存します。一時JPEGにExifを付けてから保存先へコピーするので、シークできないドキュメントプロバイダーにも保存できます。GPSは出力Exifへコピーしません。HDR/Ultra HDRのゲインマップは保持しません。

大きな写真ではメモリ不足を防ぐため、最大2400万画素かつ端末のアプリ用ヒープ容量に応じた画素数まで縮小します。書き出し中は設定と二重実行を無効化。MediaStoreのIS_PENDINGを使い、保存が完了した写真だけをギャラリーに公開します。失敗・キャンセル時の未完成ファイルは削除します。

## アセット

- `MochiyPopOne-Regular.ttf`：提供されたフォント。SIL OFL同梱。
- Google Sans Flex：[Google Fonts公式配布](https://github.com/google/fonts/tree/main/ofl/googlesansflex)。可変フォントとSIL OFLを同梱。
- `app-icon_color.png`：Google公式GitHubの[Noto 3D Emoji](https://github.com/googlefonts/noto-emoji/tree/main/3D)から取得したPNG（ユーザー申告）。画像素材は公式READMEのApache 2.0の案内に従い、帰属表示・加工内容・ライセンス全文を同梱。絵文字本体の絵柄は変更せず、ランチャー用に拡縮・配置。
- `app-icon_mono.svg`：Noto Emojiフォントをユーザーがアウトライン化したSVG。Android VectorDrawableへ変換し、テーマ対応モノクロアイコンとして使用。元フォントのSIL OFL 1.1と出典・加工内容を同梱。
- カラーアイコンの虹色背景：ユーザーがChatGPTとPythonで生成。HSBのS=100%、B=100%を維持。
- アダプティブアイコンは108dpキャンバスの66dp安全領域に前景を配置。
- Material Symbols：[Google公式アイコン](https://github.com/google/material-design-icons)、Apache 2.0。

## 主なファイル

- `ui/IkiriApp.kt`：画面・シート・コントロール・標準触覚フィードバック
- `ui/PhotoPalettePicker.kt`：横スクロールのパレット選択・12面クッキーと文字の変形
- `ui/LogoLibraryScreen.kt`：ロゴ登録・削除・選択マーク付きリストとプレビュー
- `logo/LogoRepository.kt`：SVG／透過PNGの検証とアプリ内登録
- `photo/FrameText.kt`：Exifの表示値
- `home/RollingEmoji.kt` / `RollingBall.kt` / `EmojiRenderer.kt`：センサー・球の運動・3D描画
- `ui/Theme.kt`：ダイナミックカラー・M3 Expressive
- `EditorViewModel.kt` / `EditorSessionStore.kt`：編集・下書きの永続化と復元・非同期読込・保存
- `MainActivity.kt` / `EditorActivity.kt`：Android標準の画面遷移・写真選択・共有受信
- `photo/SharedImage.kt`：共有URI検証
- `photo/FrameRenderer.kt`：プレビューと書き出しに共通の描画
- `photo/PhotoTheme.kt`：Google公式の量子化・色の順位付け・Material配色生成
- `color/`：Material Color UtilitiesのJavaソース。出典・変更点は同ディレクトリのREADMEと同梱NOTICEに記載
- `photo/PhotoRepository.kt`：Photo Picker／共有URI読込・Exif・MediaStore保存
- `src/test`：Exif整形とレイアウト境界のユニットテスト
- `src/androidTest`：戻るジェスチャー・標準触覚の呼び分け・共有受信・画像回転・保存・アイコン色・Compose UIの実行テスト

M3 Expressiveの指定サイズ・押下シェイプを利用するため、Material 3は1.5.0-alpha29を固定しています。Compose UIは1.12.1です。

参照：[Android 17 SDK](https://developer.android.com/about/versions/17/setup-sdk)、[Material 3 ButtonDefaults](https://developer.android.com/reference/kotlin/androidx/compose/material3/ButtonDefaults)、[予測型戻る](https://developer.android.com/develop/ui/compose/system/predictive-back-setup)、[標準触覚の種類](https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType)、[写真を色の抽出元にするAPI](https://developer.android.com/reference/com/google/android/material/color/DynamicColorsOptions.Builder)、[標準ファイル選択](https://developer.android.com/training/data-storage/shared/documents-files)、[AndroidSVG描画API](https://bigbadaboom.github.io/androidsvg/api_summary.html)。
