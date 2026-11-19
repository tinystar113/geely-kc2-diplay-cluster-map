.class public final Lcom/shilapi/xcertplay/media/H52NavigationBridge;
.super Ljava/lang/Object;
.source "H52NavigationBridge.java"


# static fields
.field private static final ACTION:Ljava/lang/String; = "AUTONAVI_STANDARD_BROADCAST_SEND"

.field private static final TAG:Ljava/lang/String; = "DiPlay-H52-Navi"


# instance fields
.field private active:Z

.field private final context:Landroid/content/Context;

.field private lastArrived:Z

.field private lastCurRoad:Ljava/lang/String;

.field private lastIcon:I

.field private lastNextRoad:Ljava/lang/String;

.field private lastRouteAllDis:I

.field private lastRouteRemainDis:I

.field private lastRouteRemainTime:I

.field private lastSegRemainDis:I

.field private route:Ljava/lang/Object;


# direct methods
.method constructor <init>(Landroid/content/Context;)V
    .registers 3

    .line 26
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 17
    const/4 v0, -0x1

    iput v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastSegRemainDis:I

    .line 18
    iput v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteRemainDis:I

    .line 19
    iput v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteRemainTime:I

    .line 20
    iput v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteAllDis:I

    .line 21
    const/16 v0, 0x9

    iput v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastIcon:I

    .line 22
    const/4 v0, 0x0

    iput-boolean v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastArrived:Z

    .line 23
    const-string v0, ""

    iput-object v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastNextRoad:Ljava/lang/String;

    .line 24
    const-string v0, "\u5bfc\u822a\u4e2d"

    iput-object v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastCurRoad:Ljava/lang/String;

    .line 27
    invoke-virtual {p1}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p1

    iput-object p1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->context:Landroid/content/Context;

    .line 28
    return-void
.end method

.method public static formatAutoDistance(I)Ljava/lang/String;
    .registers 5

    .line 232
    if-gtz p0, :cond_5

    const-string p0, "0\u7c73"

    return-object p0

    .line 233
    :cond_5
    const/16 v0, 0x3e8

    if-ge p0, v0, :cond_1d

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v0, "\u7c73"

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    return-object p0

    .line 234
    :cond_1d
    int-to-double v0, p0

    const-wide/high16 v2, 0x4059000000000000L    # 100.0

    invoke-static {v0, v1}, Ljava/lang/Double;->isNaN(D)Z

    div-double/2addr v0, v2

    const-wide/high16 v2, 0x3fe0000000000000L    # 0.5

    add-double/2addr v0, v2

    double-to-int p0, v0

    .line 235
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    div-int/lit8 v1, p0, 0xa

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v0

    const-string v1, "."

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    rem-int/lit8 p0, p0, 0xa

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v0, "\u516c\u91cc"

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    return-object p0
.end method

.method public static formatAutoTime(I)Ljava/lang/String;
    .registers 5

    .line 239
    if-gtz p0, :cond_5

    const-string p0, "0\u5206\u949f"

    return-object p0

    .line 240
    :cond_5
    add-int/lit8 p0, p0, 0x1e

    const/16 v0, 0x3c

    div-int/2addr p0, v0

    .line 241
    const-string v1, "\u5206\u949f"

    if-ge p0, v0, :cond_20

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    return-object p0

    .line 242
    :cond_20
    div-int/lit8 v2, p0, 0x3c

    .line 243
    rem-int/2addr p0, v0

    .line 244
    const-string v0, "\u5c0f\u65f6"

    if-nez p0, :cond_39

    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {p0, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    return-object p0

    .line 245
    :cond_39
    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v3, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    return-object p0
.end method

.method private static get(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;
    .registers 6

    .line 264
    const/4 v0, 0x0

    if-nez p0, :cond_4

    return-object v0

    .line 266
    :cond_4
    :try_start_4
    invoke-virtual {p0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    move-result-object v1

    const/4 v2, 0x0

    new-array v3, v2, [Ljava/lang/Class;

    invoke-virtual {v1, p1, v3}, Ljava/lang/Class;->getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object p1

    new-array v1, v2, [Ljava/lang/Object;

    invoke-virtual {p1, p0, v1}, Ljava/lang/reflect/Method;->invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p0
    :try_end_15
    .catch Ljava/lang/Exception; {:try_start_4 .. :try_end_15} :catch_16

    return-object p0

    .line 267
    :catch_16
    move-exception p0

    .line 268
    return-object v0
.end method

.method private static getField(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;
    .registers 4

    .line 253
    const/4 v0, 0x0

    if-nez p0, :cond_4

    return-object v0

    .line 255
    :cond_4
    :try_start_4
    invoke-virtual {p0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    move-result-object v1

    invoke-virtual {v1, p1}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object p1

    .line 256
    const/4 v1, 0x1

    invoke-virtual {p1, v1}, Ljava/lang/reflect/Field;->setAccessible(Z)V

    .line 257
    invoke-virtual {p1, p0}, Ljava/lang/reflect/Field;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p0
    :try_end_14
    .catch Ljava/lang/Exception; {:try_start_4 .. :try_end_14} :catch_15

    return-object p0

    .line 258
    :catch_15
    move-exception p0

    .line 259
    return-object v0
.end method

.method private hasGuidanceData()Z
    .registers 2

    .line 204
    iget v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteRemainDis:I

    if-gez v0, :cond_15

    iget v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastSegRemainDis:I

    if-gez v0, :cond_15

    iget-object v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastNextRoad:Ljava/lang/String;

    if-eqz v0, :cond_13

    invoke-virtual {v0}, Ljava/lang/String;->length()I

    move-result v0

    if-lez v0, :cond_13

    goto :goto_15

    :cond_13
    const/4 v0, 0x0

    goto :goto_16

    :cond_15
    :goto_15
    const/4 v0, 0x1

    :goto_16
    return v0
.end method

.method public static icon(I)I
    .registers 3

    .line 273
    const/16 v0, 0x1c

    const/16 v1, 0xc

    if-lt p0, v0, :cond_b

    const/16 v0, 0x2e

    if-gt p0, v0, :cond_b

    return v1

    .line 274
    :cond_b
    const/16 v0, 0x9

    packed-switch p0, :pswitch_data_2e

    .line 288
    :pswitch_10
    return v0

    .line 287
    :pswitch_11
    const/4 p0, 0x7

    return p0

    .line 286
    :pswitch_13
    const/4 p0, 0x6

    return p0

    .line 285
    :pswitch_15
    const/4 p0, 0x5

    return p0

    .line 284
    :pswitch_17
    const/4 p0, 0x4

    return p0

    .line 283
    :pswitch_19
    const/4 p0, 0x1

    return p0

    .line 282
    :pswitch_1b
    const/16 p0, 0xf

    return p0

    .line 281
    :pswitch_1e
    const/16 p0, 0xa

    return p0

    .line 280
    :pswitch_21
    return v1

    .line 279
    :pswitch_22
    const/16 p0, 0xb

    return p0

    .line 278
    :pswitch_25
    const/16 p0, 0x8

    return p0

    .line 277
    :pswitch_28
    const/4 p0, 0x3

    return p0

    .line 276
    :pswitch_2a
    const/4 p0, 0x2

    return p0

    .line 275
    :pswitch_2c
    return v0

    nop

    :pswitch_data_2e
    .packed-switch 0x0
        :pswitch_2c
        :pswitch_2a
        :pswitch_28
        :pswitch_2c
        :pswitch_25
        :pswitch_2c
        :pswitch_22
        :pswitch_21
        :pswitch_1e
        :pswitch_1e
        :pswitch_1b
        :pswitch_19
        :pswitch_1b
        :pswitch_17
        :pswitch_15
        :pswitch_1e
        :pswitch_1e
        :pswitch_1e
        :pswitch_25
        :pswitch_22
        :pswitch_2a
        :pswitch_28
        :pswitch_17
        :pswitch_15
        :pswitch_1b
        :pswitch_1b
        :pswitch_25
        :pswitch_1b
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_10
        :pswitch_13
        :pswitch_11
        :pswitch_17
        :pswitch_15
        :pswitch_15
        :pswitch_17
        :pswitch_15
    .end packed-switch
.end method

.method public static isArrival(I)Z
    .registers 2

    .line 249
    const/16 v0, 0xa

    if-eq p0, v0, :cond_17

    const/16 v0, 0xc

    if-eq p0, v0, :cond_17

    const/16 v0, 0x18

    if-eq p0, v0, :cond_17

    const/16 v0, 0x19

    if-eq p0, v0, :cond_17

    const/16 v0, 0x1b

    if-ne p0, v0, :cond_15

    goto :goto_17

    :cond_15
    const/4 p0, 0x0

    goto :goto_18

    :cond_17
    :goto_17
    const/4 p0, 0x1

    :goto_18
    return p0
.end method

.method private publishGuidance()V
    .registers 11

    .line 157
    invoke-direct {p0}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->hasGuidanceData()Z

    move-result v0

    if-nez v0, :cond_7

    return-void

    .line 159
    :cond_7
    new-instance v0, Landroid/content/Intent;

    const-string v1, "AUTONAVI_STANDARD_BROADCAST_SEND"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 160
    const-string v1, "KEY_TYPE"

    const/16 v2, 0x2711

    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    .line 162
    iget-object v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastCurRoad:Ljava/lang/String;

    .line 163
    if-eqz v1, :cond_2b

    invoke-virtual {v1}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/String;->length()I

    move-result v2

    if-eqz v2, :cond_2b

    const-string v2, "null"

    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_2d

    .line 164
    :cond_2b
    const-string v1, "\u5bfc\u822a\u4e2d"

    .line 166
    :cond_2d
    const-string v2, "CUR_ROAD_NAME"

    invoke-virtual {v0, v2, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 167
    iget-object v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastNextRoad:Ljava/lang/String;

    const-string v3, ""

    if-eqz v2, :cond_39

    goto :goto_3a

    :cond_39
    move-object v2, v3

    :goto_3a
    const-string v4, "NEXT_ROAD_NAME"

    invoke-virtual {v0, v4, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 169
    iget v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastSegRemainDis:I

    const/4 v4, 0x0

    if-ltz v2, :cond_45

    goto :goto_46

    :cond_45
    const/4 v2, 0x0

    .line 170
    :goto_46
    iget v5, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteRemainDis:I

    if-ltz v5, :cond_4b

    goto :goto_4c

    :cond_4b
    const/4 v5, 0x0

    .line 171
    :goto_4c
    iget v6, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteRemainTime:I

    if-ltz v6, :cond_51

    goto :goto_52

    :cond_51
    const/4 v6, 0x0

    .line 172
    :goto_52
    iget v7, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteAllDis:I

    if-ltz v7, :cond_57

    goto :goto_58

    :cond_57
    move v7, v5

    .line 174
    :goto_58
    const-string v8, "SEG_REMAIN_DIS"

    invoke-virtual {v0, v8, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    .line 175
    const-string v8, "ROUTE_REMAIN_DIS"

    invoke-virtual {v0, v8, v5}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    .line 176
    const-string v8, "ROUTE_REMAIN_DIS_AUTO"

    invoke-static {v5}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->formatAutoDistance(I)Ljava/lang/String;

    move-result-object v9

    invoke-virtual {v0, v8, v9}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 177
    const-string v8, "ROUTE_ALL_DIS"

    invoke-virtual {v0, v8, v7}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    .line 178
    const-string v7, "ROUTE_REMAIN_TIME"

    invoke-virtual {v0, v7, v6}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    .line 179
    const-string v7, "ROUTE_REMAIN_TIME_AUTO"

    invoke-static {v6}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->formatAutoTime(I)Ljava/lang/String;

    move-result-object v8

    invoke-virtual {v0, v7, v8}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 180
    iget v7, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastIcon:I

    if-ltz v7, :cond_83

    goto :goto_85

    :cond_83
    const/16 v7, 0x9

    :goto_85
    const-string v8, "ICON"

    invoke-virtual {v0, v8, v7}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    .line 181
    const-string v7, "ARRIVE_STATUS"

    iget-boolean v8, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastArrived:Z

    invoke-virtual {v0, v7, v8}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 183
    nop

    .line 185
    :try_start_92
    new-instance v7, Lorg/json/JSONObject;

    invoke-direct {v7}, Lorg/json/JSONObject;-><init>()V

    .line 186
    const-string v8, "ditance"

    invoke-static {v4, v2}, Ljava/lang/Math;->max(II)I

    move-result v9

    invoke-virtual {v7, v8, v9}, Lorg/json/JSONObject;->put(Ljava/lang/String;I)Lorg/json/JSONObject;

    .line 187
    const-string v8, "time"

    invoke-static {v4, v6}, Ljava/lang/Math;->max(II)I

    move-result v4

    invoke-virtual {v7, v8, v4}, Lorg/json/JSONObject;->put(Ljava/lang/String;I)Lorg/json/JSONObject;

    .line 188
    const-string v4, "method"

    iget-object v8, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastNextRoad:Ljava/lang/String;

    if-eqz v8, :cond_b0

    move-object v3, v8

    :cond_b0
    invoke-virtual {v7, v4, v3}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    .line 189
    new-instance v3, Lorg/json/JSONArray;

    invoke-direct {v3}, Lorg/json/JSONArray;-><init>()V

    .line 190
    invoke-virtual {v3, v7}, Lorg/json/JSONArray;->put(Ljava/lang/Object;)Lorg/json/JSONArray;

    .line 191
    new-instance v4, Lorg/json/JSONObject;

    invoke-direct {v4}, Lorg/json/JSONObject;-><init>()V

    .line 192
    const-string v7, "path_info"

    invoke-virtual {v4, v7, v3}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    .line 193
    invoke-virtual {v4}, Lorg/json/JSONObject;->toString()Ljava/lang/String;

    move-result-object v3
    :try_end_c9
    .catchall {:try_start_92 .. :try_end_c9} :catchall_ca

    .line 196
    goto :goto_d4

    .line 194
    :catchall_ca
    move-exception v3

    .line 195
    const-string v4, "DiPlay-H52-Navi"

    const-string v7, "build EXTRA_ROAD_INFO failed"

    invoke-static {v4, v7, v3}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    const-string v3, "{\"path_info\":[]}"

    .line 197
    :goto_d4
    const-string v4, "EXTRA_ROAD_INFO"

    invoke-virtual {v0, v4, v3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 199
    iget-object v3, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->context:Landroid/content/Context;

    invoke-virtual {v3, v0}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V

    .line 200
    iget-object v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->context:Landroid/content/Context;

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "NAV out icon="

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    iget v4, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastIcon:I

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v4, " dis="

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    const-string v3, " rem="

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2, v5}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    const-string v3, " time="

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    const-string v3, " cur="

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/shilapi/xcertplay/ui/H52Diagnostics;->record(Landroid/content/Context;Ljava/lang/String;)V

    .line 201
    return-void
.end method

.method private status(I)V
    .registers 6

    # H52 cluster map: navigation started (8) / ended (9) switches the cluster map drawing.
    invoke-static {p1}, Lcom/shilapi/xcertplay/media/GeelyClusterMap;->onNavStatus(I)V

    .line 226
    iget-object v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->context:Landroid/content/Context;

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "NAV state="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/shilapi/xcertplay/ui/H52Diagnostics;->record(Landroid/content/Context;Ljava/lang/String;)V

    .line 227
    iget-object v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->context:Landroid/content/Context;

    new-instance v1, Landroid/content/Intent;

    const-string v2, "AUTONAVI_STANDARD_BROADCAST_SEND"

    invoke-direct {v1, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 228
    const-string v2, "KEY_TYPE"

    const/16 v3, 0x2723

    invoke-virtual {v1, v2, v3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    move-result-object v1

    const-string v2, "EXTRA_STATE"

    invoke-virtual {v1, v2, p1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    move-result-object p1

    .line 227
    invoke-virtual {v0, p1}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V

    .line 229
    return-void
.end method


# virtual methods
.method accept(Ljava/lang/Object;)V
    .registers 12

    .line 31
    if-nez p1, :cond_3

    return-void

    .line 33
    :cond_3
    :try_start_3
    const-string v0, "getMessageId"

    invoke-static {p1, v0}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->get(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    .line 34
    instance-of v1, v0, Ljava/lang/Number;

    if-nez v1, :cond_e

    return-void

    .line 35
    :cond_e
    check-cast v0, Ljava/lang/Number;

    invoke-virtual {v0}, Ljava/lang/Number;->intValue()I

    move-result v0

    .line 36
    const/16 v1, 0x5201

    if-eq v0, v1, :cond_1d

    const/16 v1, 0x5202

    if-eq v0, v1, :cond_1d

    return-void

    .line 38
    :cond_1d
    iget-object v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->route:Ljava/lang/Object;

    if-nez v1, :cond_2d

    .line 39
    const-string v1, "com.shilapi.xcertplay.hud.BydHudRouteState"

    invoke-static {v1}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/Class;->newInstance()Ljava/lang/Object;

    move-result-object v1

    iput-object v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->route:Ljava/lang/Object;

    .line 42
    :cond_2d
    const-string v1, "getPayload"

    invoke-static {p1, v1}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->get(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, [B

    .line 43
    if-nez p1, :cond_38

    return-void

    .line 45
    :cond_38
    iget-object v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->route:Ljava/lang/Object;

    invoke-virtual {v1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    move-result-object v1

    const-string v2, "accept"

    const/4 v3, 0x2

    new-array v4, v3, [Ljava/lang/Class;

    sget-object v5, Ljava/lang/Integer;->TYPE:Ljava/lang/Class;

    const/4 v6, 0x0

    aput-object v5, v4, v6

    const-class v5, [B

    const/4 v7, 0x1

    aput-object v5, v4, v7

    invoke-virtual {v1, v2, v4}, Ljava/lang/Class;->getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object v1

    iget-object v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->route:Ljava/lang/Object;

    new-array v3, v3, [Ljava/lang/Object;

    .line 46
    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v4

    aput-object v4, v3, v6

    aput-object p1, v3, v7

    invoke-virtual {v1, v2, v3}, Ljava/lang/reflect/Method;->invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    .line 48
    iget-object v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->context:Landroid/content/Context;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "NAV input="

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v0

    const-string v2, " parsed="

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/shilapi/xcertplay/ui/H52Diagnostics;->record(Landroid/content/Context;Ljava/lang/String;)V

    .line 50
    const-string v0, "CLEAR"

    invoke-static {p1}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_93

    .line 51
    invoke-virtual {p0}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->stop()V

    .line 52
    return-void

    .line 55
    :cond_93
    iget-object p1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->route:Ljava/lang/Object;

    const-string v0, "routeActive"

    invoke-static {p1, v0}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->getField(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p1

    .line 56
    sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;

    invoke-virtual {v0, p1}, Ljava/lang/Boolean;->equals(Ljava/lang/Object;)Z

    move-result p1

    .line 57
    const/16 v0, 0x8

    if-eqz p1, :cond_ae

    iget-boolean v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->active:Z

    if-nez v1, :cond_ae

    .line 58
    iput-boolean v7, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->active:Z

    .line 59
    invoke-direct {p0, v0}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->status(I)V

    .line 62
    :cond_ae
    iget-object v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->route:Ljava/lang/Object;

    const-string v2, "remainingMeters"

    invoke-static {v1, v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->getField(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v1

    .line 63
    instance-of v2, v1, Ljava/lang/Number;

    const-wide/32 v3, 0x7fffffff

    const-wide/16 v5, 0x0

    if-eqz v2, :cond_da

    .line 64
    check-cast v1, Ljava/lang/Number;

    invoke-virtual {v1}, Ljava/lang/Number;->longValue()J

    move-result-wide v1

    .line 65
    cmp-long v8, v1, v5

    if-ltz v8, :cond_da

    .line 66
    invoke-static {v3, v4, v1, v2}, Ljava/lang/Math;->min(JJ)J

    move-result-wide v1

    long-to-int v2, v1

    .line 67
    iget v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteAllDis:I

    if-ltz v1, :cond_d6

    add-int/lit16 v1, v1, 0x1f4

    if-le v2, v1, :cond_d8

    .line 68
    :cond_d6
    iput v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteAllDis:I

    .line 70
    :cond_d8
    iput v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteRemainDis:I

    .line 74
    :cond_da
    iget-object v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->route:Ljava/lang/Object;

    const-string v2, "remainingSeconds"

    invoke-static {v1, v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->getField(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v1

    .line 75
    instance-of v2, v1, Ljava/lang/Number;

    if-eqz v2, :cond_f7

    .line 76
    check-cast v1, Ljava/lang/Number;

    invoke-virtual {v1}, Ljava/lang/Number;->longValue()J

    move-result-wide v1

    .line 77
    cmp-long v8, v1, v5

    if-ltz v8, :cond_f7

    .line 78
    invoke-static {v3, v4, v1, v2}, Ljava/lang/Math;->min(JJ)J

    move-result-wide v1

    long-to-int v2, v1

    iput v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteRemainTime:I

    .line 82
    :cond_f7
    iget-object v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->route:Ljava/lang/Object;

    const-string v2, "distanceMeters"

    invoke-static {v1, v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->getField(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v1

    .line 83
    instance-of v2, v1, Ljava/lang/Number;

    if-eqz v2, :cond_114

    .line 84
    check-cast v1, Ljava/lang/Number;

    invoke-virtual {v1}, Ljava/lang/Number;->longValue()J

    move-result-wide v1

    .line 85
    cmp-long v8, v1, v5

    if-ltz v8, :cond_114

    .line 86
    invoke-static {v3, v4, v1, v2}, Ljava/lang/Math;->min(JJ)J

    move-result-wide v1

    long-to-int v2, v1

    iput v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastSegRemainDis:I

    .line 90
    :cond_114
    iget-object v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->route:Ljava/lang/Object;

    const-string v2, "currentRoad"

    invoke-static {v1, v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->getField(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v1

    .line 91
    instance-of v2, v1, Ljava/lang/String;
    :try_end_11e
    .catch Ljava/lang/Exception; {:try_start_3 .. :try_end_11e} :catch_1f3

    const-string v8, "null"

    if-eqz v2, :cond_136

    .line 92
    :try_start_122
    check-cast v1, Ljava/lang/String;

    invoke-virtual {v1}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object v1

    .line 93
    invoke-virtual {v1}, Ljava/lang/String;->length()I

    move-result v2

    if-lez v2, :cond_136

    invoke-virtual {v8, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_136

    .line 94
    iput-object v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastCurRoad:Ljava/lang/String;

    .line 98
    :cond_136
    iget-object v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->route:Ljava/lang/Object;

    const-string v2, "currentApple"

    invoke-static {v1, v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->get(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v1

    .line 99
    if-eqz v1, :cond_1d7

    .line 100
    const-string v2, "getType"

    invoke-static {v1, v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->get(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v2

    .line 101
    instance-of v9, v2, Ljava/lang/Number;

    if-eqz v9, :cond_15e

    .line 102
    check-cast v2, Ljava/lang/Number;

    invoke-virtual {v2}, Ljava/lang/Number;->intValue()I

    move-result v2

    .line 103
    invoke-static {v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->icon(I)I

    move-result v9

    .line 104
    if-ltz v9, :cond_158

    .line 105
    iput v9, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastIcon:I

    .line 107
    :cond_158
    invoke-static {v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->isArrival(I)Z

    move-result v2

    iput-boolean v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastArrived:Z

    .line 110
    :cond_15e
    const-string v2, "getRoad"

    invoke-static {v1, v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->get(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v2

    .line 111
    if-eqz v2, :cond_17c

    .line 112
    invoke-static {v2}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object v2

    .line 113
    invoke-virtual {v2}, Ljava/lang/String;->length()I

    move-result v9

    if-lez v9, :cond_17c

    invoke-virtual {v8, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v8

    if-nez v8, :cond_17c

    .line 114
    iput-object v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastNextRoad:Ljava/lang/String;

    .line 118
    :cond_17c
    const-string v2, "getDistanceMeters"

    invoke-static {v1, v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->get(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v2

    .line 119
    instance-of v8, v2, Ljava/lang/Number;

    if-eqz v8, :cond_197

    .line 120
    check-cast v2, Ljava/lang/Number;

    invoke-virtual {v2}, Ljava/lang/Number;->longValue()J

    move-result-wide v8

    .line 121
    cmp-long v2, v8, v5

    if-ltz v2, :cond_197

    invoke-static {v3, v4, v8, v9}, Ljava/lang/Math;->min(JJ)J

    move-result-wide v8

    long-to-int v2, v8

    iput v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastSegRemainDis:I

    .line 124
    :cond_197
    const-string v2, "getRemainingMeters"

    invoke-static {v1, v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->get(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v2

    .line 125
    instance-of v8, v2, Ljava/lang/Number;

    if-eqz v8, :cond_1bc

    .line 126
    check-cast v2, Ljava/lang/Number;

    invoke-virtual {v2}, Ljava/lang/Number;->longValue()J

    move-result-wide v8

    .line 127
    cmp-long v2, v8, v5

    if-ltz v2, :cond_1bc

    .line 128
    invoke-static {v3, v4, v8, v9}, Ljava/lang/Math;->min(JJ)J

    move-result-wide v8

    long-to-int v2, v8

    .line 129
    iget v8, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteAllDis:I

    if-ltz v8, :cond_1b8

    add-int/lit16 v8, v8, 0x1f4

    if-le v2, v8, :cond_1ba

    .line 130
    :cond_1b8
    iput v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteAllDis:I

    .line 132
    :cond_1ba
    iput v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteRemainDis:I

    .line 136
    :cond_1bc
    const-string v2, "getRemainingSeconds"

    invoke-static {v1, v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->get(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v2

    .line 137
    instance-of v8, v2, Ljava/lang/Number;

    if-eqz v8, :cond_1d7

    .line 138
    check-cast v2, Ljava/lang/Number;

    invoke-virtual {v2}, Ljava/lang/Number;->longValue()J

    move-result-wide v8

    .line 139
    cmp-long v2, v8, v5

    if-ltz v2, :cond_1d7

    invoke-static {v3, v4, v8, v9}, Ljava/lang/Math;->min(JJ)J

    move-result-wide v2

    long-to-int v3, v2

    iput v3, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteRemainTime:I

    .line 143
    :cond_1d7
    iget-boolean v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->active:Z

    if-nez v2, :cond_1ea

    if-nez p1, :cond_1e5

    if-nez v1, :cond_1e5

    invoke-direct {p0}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->hasGuidanceData()Z

    move-result p1

    if-eqz p1, :cond_1ea

    .line 144
    :cond_1e5
    iput-boolean v7, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->active:Z

    .line 145
    invoke-direct {p0, v0}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->status(I)V

    .line 148
    :cond_1ea
    iget-boolean p1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->active:Z

    if-nez p1, :cond_1ef

    return-void

    .line 150
    :cond_1ef
    invoke-direct {p0}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->publishGuidance()V
    :try_end_1f2
    .catch Ljava/lang/Exception; {:try_start_122 .. :try_end_1f2} :catch_1f3

    .line 153
    goto :goto_1fb

    .line 151
    :catch_1f3
    move-exception p1

    .line 152
    const-string v0, "DiPlay-H52-Navi"

    const-string v1, "Guidance rejected"

    invoke-static {v0, v1, p1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 154
    :goto_1fb
    return-void
.end method

.method stop()V
    .registers 5

    .line 208
    iget-boolean v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->active:Z

    const/4 v1, 0x0

    const/16 v2, 0x9

    if-eqz v0, :cond_c

    .line 209
    invoke-direct {p0, v2}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->status(I)V

    .line 210
    iput-boolean v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->active:Z

    .line 212
    :cond_c
    iget-object v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->route:Ljava/lang/Object;

    if-eqz v0, :cond_17

    .line 213
    :try_start_10
    const-string v3, "clear"

    invoke-static {v0, v3}, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->get(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;
    :try_end_15
    .catch Ljava/lang/Exception; {:try_start_10 .. :try_end_15} :catch_16

    goto :goto_17

    :catch_16
    move-exception v0

    .line 215
    :cond_17
    :goto_17
    const/4 v0, -0x1

    iput v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastSegRemainDis:I

    .line 216
    iput v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteRemainDis:I

    .line 217
    iput v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteRemainTime:I

    .line 218
    iput v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastRouteAllDis:I

    .line 219
    iput v2, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastIcon:I

    .line 220
    iput-boolean v1, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastArrived:Z

    .line 221
    const-string v0, ""

    iput-object v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastNextRoad:Ljava/lang/String;

    .line 222
    const-string v0, "\u5bfc\u822a\u4e2d"

    iput-object v0, p0, Lcom/shilapi/xcertplay/media/H52NavigationBridge;->lastCurRoad:Ljava/lang/String;

    .line 223
    return-void
.end method
