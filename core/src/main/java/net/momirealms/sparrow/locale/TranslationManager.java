package net.momirealms.sparrow.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.translation.Translator;
import net.momirealms.sparrow.locale.tag.IndexedArgumentTag;
import net.momirealms.sparrow.util.AdventureHelper;
import org.incendo.cloud.suggestion.Suggestion;
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
    List<Suggestion> ALL_LANG_SUGGESTIONS = ALL_LANG.stream().map(Suggestion::suggestion).toList();
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
     * 在翻译管理器创建后调用.
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
        return miniMessageTranslation(key, null);
    }

    /**
     * 使用指定语言环境渲染一个可翻译组件.
     *
     * @param component 需要渲染的可翻译组件
     * @param locale 目标语言环境, 传入 null 时由实现决定使用当前选定语言
     * @return 渲染后的 Adventure 组件
     */
    Component render(TranslatableComponent component, @Nullable Locale locale);

    default Component render(TranslatableComponent component) {
        return render(component, null);
    }

    default Component render(TranslatableComponent.Builder key, @Nullable Locale locale) {
        return this.render((TranslatableComponent) key.asComponent(), locale);
    }

    /**
     * 查询指定翻译键并将结果渲染为纯文本字符串.
     * 该方法会先获取 MiniMessage 翻译文本, 再使用按索引参数解析器填充参数, 最后提取纯文本结果.
     *
     * @param key 翻译键
     * @param locale 目标语言环境, 传入 null 时由实现决定使用当前选定语言
     * @param arguments 用于替换翻译模板中索引占位符的参数列表
     * @return 渲染后的纯文本字符串, 若翻译缺失则返回原始键
     */
    default String plainTranslation(String key, @Nullable Locale locale, String... arguments) {
        String translation = miniMessageTranslation(key, locale);
        if (translation == null) {
            return key;
        }
        Component deserialize = AdventureHelper.customMiniMessage().deserialize(translation, new IndexedArgumentTag(Arrays.stream(arguments).map(Component::text).toList()));
        return AdventureHelper.plainTextContent(deserialize);
    }

    default String plainTranslation(String key, String... arguments) {
        String translation = miniMessageTranslation(key);
        if (translation == null) {
            return key;
        }
        Component deserialize = AdventureHelper.customMiniMessage().deserialize(translation, new IndexedArgumentTag(Arrays.stream(arguments).map(Component::text).toList()));
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

    /**
     * 获取当前已加载的全部服务端翻译键集合.
     *
     * @return 翻译键集合
     */
    Set<String> translationKeys();

    /**
     * 按指定翻译键向控制台输出一条已完成参数替换的本地化消息.
     *
     * @param id 翻译键
     * @param args 用于填充索引占位符的参数列表
     */
    void log(String id, String... args);

    /**
     * 获取客户端语言数据Map的只读视图.
     *
     * @return 获取客户端语言数据Map的只读视图.
     */
    Map<String, ClientLangData> clientLangData();

    /**
     * 向指定客户端语言或语言组注册翻译数据.
     * 实现可支持完整语言标识, 语言前缀或 `all` 特殊标识.
     *
     * @param langId 目标语言标识, 如 `zh_cn`, `zh` 或 `all`
     * @param translations 要追加的翻译键值对
     */
    void addClientTranslation(String langId, Map<String, String> translations);
}
