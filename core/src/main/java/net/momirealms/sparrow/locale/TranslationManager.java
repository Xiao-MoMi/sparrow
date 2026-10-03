package net.momirealms.sparrow.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.Translator;
import net.momirealms.sparrow.locale.tag.IndexedArgumentTag;
import net.momirealms.sparrow.util.AdventureHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public interface TranslationManager {
    Set<String> ALL_LANG = Set.of(
            "af_za", "ar_sa", "ast_es", "az_az", "ba_ru", "bar", "be_by", "be_latn",
            "bg_bg", "br_fr", "brb", "bs_ba", "ca_es", "cs_cz", "cy_gb", "da_dk",
            "de_at", "de_ch", "de_de", "el_gr", "en_au", "en_ca", "en_gb", "en_nz",
            "en_pt", "en_ud", "en_us", "enp", "enws", "eo_uy", "es_ar", "es_cl",
            "es_ec", "es_es", "es_mx", "es_uy", "es_ve", "esan", "et_ee", "eu_es",
            "fa_ir", "fi_fi", "fil_ph", "fo_fo", "fr_ca", "fr_fr", "fra_de", "fur_it",
            "fy_nl", "ga_ie", "gd_gb", "gl_es", "haw_us", "he_il", "hi_in", "hn_no",
            "hr_hr", "hu_hu", "hy_am", "id_id", "ig_ng", "io_en", "is_is", "isv",
            "it_it", "ja_jp", "jbo_en", "ka_ge", "kk_kz", "kn_in", "ko_kr", "ksh",
            "kw_gb", "ky_kg", "la_la", "lb_lu", "li_li", "lmo", "lo_la", "lol_us", "lt_lt",
            "lv_lv", "lzh", "mk_mk", "mn_mn", "ms_my", "mt_mt", "nah", "nds_de",
            "nl_be", "nl_nl", "nn_no", "no_no", "oc_fr", "ovd", "pl_pl", "pls",
            "pt_br", "pt_pt", "qya_aa", "ro_ro", "rpr", "ru_ru", "ry_ua", "sah_sah",
            "se_no", "sk_sk", "sl_si", "so_so", "sq_al", "sr_cs", "sr_sp", "sv_se",
            "sxu", "szl", "ta_in", "th_th", "tl_ph", "tlh_aa", "tok", "tr_tr",
            "tt_ru", "tzo_mx", "uk_ua", "val_es", "vec_it", "vi_vn", "vp_vl", "yi_de",
            "yo_ng", "zh_cn", "zh_hk", "zh_tw", "zlm_arab"
    );
    Map<String, List<String>> LOCALE_2_COUNTRIES = ALL_LANG.stream()
            .map(lang -> lang.split("_"))
            .filter(split -> split.length >= 2)
            .collect(Collectors.groupingBy(
                    split -> split[0],
                    Collectors.mapping(split -> split[1], Collectors.toUnmodifiableList())
            ));

    static TranslationManager instance() {
        return TranslationManagerImpl.instance;
    }

    /**
     * 按控制台语言生成纯文本日志, 保留 PluginLogger 的级别和插件前缀.
     *
     * @param key 日志翻译键
     * @param arguments 按索引填充的文本参数
     * @return 不含颜色控制符的日志文本
     */
    static String console(String key, String... arguments) {
        return TranslationManagerImpl.instance.plainTranslation(key, arguments);
    }

    void reload();

    /**
     * 查询指定翻译键在目标语言环境下的 MiniMessage 翻译文本.
     *
     * @param key 翻译键
     * @param locale 目标语言环境, 传入 null 时由实现决定使用当前选定语言
     * @return 翻译后的 MiniMessage 字符串
     */
    String miniMessageTranslation(String key, @Nullable Locale locale);

    default String miniMessageTranslation(String key) {
        return this.miniMessageTranslation(key, null);
    }

    /**
     * 按指定语言展开服务端语言的翻译键, 其他翻译键交给 Adventure 默认处理.
     */
    @NotNull
    Component render(@NotNull Component component, @Nullable Locale locale);

    @NotNull
    default Component render(@NotNull Component component) {
        return this.render(component, null);
    }

    default String plainTranslation(String key, String... arguments) {
        String translation = this.miniMessageTranslation(key);
        if (translation == null) {
            return key;
        }
        Component deserialize = AdventureHelper.customMiniMessage()
                .deserialize(translation, new IndexedArgumentTag(Arrays.stream(arguments).map(Component::text).toList()));
        return AdventureHelper.plainTextContent(deserialize);
    }

    /**
     * 将语言环境字符串解析为 `Locale` 对象.
     * 若输入为 null 或空字符串, 则返回 null.
     *
     * @param locale 形如 `zh_cn` 或 `en_us` 的语言环境字符串
     * @return 解析得到的 `Locale`, 若输入为空则返回 null
     */
    static @Nullable Locale parseLocale(@Nullable String locale) {
        return locale == null || locale.isEmpty() ? null : Translator.parseLocale(locale);
    }

    /**
     * 将 `Locale` 格式化为项目内使用的语言标识字符串.
     * 当国家或地区部分为空时仅返回语言代码, 否则返回 `language_country` 形式.
     *
     * @param locale 需要格式化的语言环境对象
     * @return 格式化后的语言标识字符串
     */
    static String formatLocale(Locale locale) {
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        String country = locale.getCountry().toLowerCase(Locale.ROOT);
        if (country.isEmpty()) {
            return language;
        } else {
            return language + "_" + country;
        }
    }
}