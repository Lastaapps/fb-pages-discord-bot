@file:Suppress("ktlint:standard:max-line-length")


import cz.lastaapps.createClient
import cz.lastaapps.downloadEvent
import cz.lastaapps.downloadFeed
import cz.lastaapps.downloadPost
import cz.lastaapps.model.Event
import cz.lastaapps.model.Post
import cz.lastaapps.model.ReferencedPost
import cz.lastaapps.parser.FacebookEventParser
import cz.lastaapps.parser.FacebookFeedParser
import cz.lastaapps.parser.FacebookPostParser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import it.skrape.selects.ElementNotFoundException
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toInstant

class FacebookTests : StringSpec(
    {
        val pageName = "siliconhill"
        val pageIdSH = "100064590618084"
        val pageIdSU = "100064544208257"

        val client = createClient()
        "feed" {
            val body = downloadFeed(client, pageName)
            FacebookFeedParser.parsePostsFromFeed(body).forEach(::println)
        }
        "post_with_reference_and_image" {
            val postId = "pfbid0FEtvWKB8pH5eB1mWGBT8MGQssGu7xdLsLvRkmzQ5D6Wdy4Gs6GbrLzUwcxWwXVz7l"
            val body = downloadPost(client, pageIdSH, postId)
            FacebookPostParser.parsePost(body, pageIdSH, postId).also(::println) shouldBe
                Post(
                    id = postId,
                    pageId = pageIdSH,
                    publishedAt = LocalDateTime(2024, 6, 12, 19, 17).toInstant(UTC),
                    author = "Silicon Hill",
                    description = "‼️Dopravni omezení v okolí kolejí‼\nBěhem následujícího víkendu bude výrazně omezená 🚗🚍 doprava v okolí kolejí. Více info dále v příspěvku.",
                    images = listOf("https://scontent-prg1-1.xx.fbcdn.net/v/t39.30808-6/448227480_432938589701815_4571644755950555224_n.jpg?stp=cp0_dst-jpg_e15_q65_s240x240&_nc_cat=105&ccb=1-7&_nc_sid=5f2048&efg=eyJpIjoiYiJ9&_nc_ohc=B--bNNj_bvUQ7kNvgE92hUW&_nc_ht=scontent-prg1-1.xx&oh=00_AYBPfRqkCAWyacKKhF3nHOBvhkl77C3vQu1RmULtSOgJRQ&oe=6677A655"),
                    links = emptyList(),
                    eventId = null,
                    references =
                    ReferencedPost(
                        author = "Správa účelových zařízení ČVUT v Praze",
                        description = "Nadšenci cyklistiky, pozor! V sobotu 15. 6. se na Strahově koná další ročník závodu L'Etape Czech Republic by Tour de France. 🚴 V areálu kolejí Strahov se postupně rozrůstá zázemí pro závodníky i organizátory - L'Etape vesnička . 🚴‍♂️",
                    ),
                )
        }
        "post_with_reference_and_link" {
            val postId = "pfbid0hqVHBmAeCZHhJzu1YJBHqJwf83nfJjsjdju5CYNzamNFfMsViW3Tp6i688UQRGMsl"
            val body = downloadPost(client, pageIdSH, postId)
            FacebookPostParser.parsePost(body, pageIdSH, postId).also(::println) shouldBe
                Post(
                    id = postId,
                    pageId = pageIdSH,
                    publishedAt = LocalDateTime(2024, 5, 27, 14, 13).toInstant(UTC),
                    author = "Silicon Hill",
                    description = "Důležité! Pro Hudebny SH dochází k 1.7.2024 ke změně výše členského příspěvku!",
                    images = emptyList(),
                    links =
                    listOf(
                        "https://zapisy.sh.cvut.cz/",
                        "https://hudebny.sh.cvut.cz/assets/docs/Pravidla_Hudebny_1.0.pdf",
                    ),
                    eventId = null,
                    references =
                    ReferencedPost(
                        author = "Hudebny SH",
                        description = "Zdravím všechny členy strahovských Hudeben! Přicházím za vámi se zprávou, ze které někteří možná nebudete úplně nadšení, nicméně věřím, že pro ni určitě všichni budete mít pochopení a najdou se i tací, kteří to naopak ocení. Určitě všichni víte v jakém stavu je většina našeho vybavení Hudeben. Mixážní pulty, bicí soupravy, kytarová a basová komba i reprobedny jsou často zastaralé, ne úplně spolehlivé a 100% funkční. Jako správci se samozřejmě snažíme opravovat a co nejdéle udržovat při životě všechno, co se dá a co je v našich silách, nicméně si uvědomujeme, že tento stav není dlouhodobě udržitelný. Proto bychom rádi občas zainvestovali do nějakého nového a modernějšího kousku, než jen stále dokola opravovat už staré a opotřebované aparáty. Rozpočet, který máme jako projekt klubu Silicon Hill k dispozici, však v současnosti na takové investice hodnotíme jako nedostatečný (do rozpočtu celého klubu, včetně našeho projektu, může nahlédnout každý člen klubu v ISu pod kapitolou Finance – Rozpočet klubu Silicon Hill 2024). Tento rozpočet je zároveň ovlivněn celkovými příjmy klubu za členské příspěvky za službu Hudebny. Současná výše příspěvku je 500 Kč za půl roku. Hodnotu členského příspěvku bychom pro vás rádi drželi co nejnižší i nadále, zároveň je ale pro klub žádoucí nějaké příjmy mít, aby bylo možno projekt financovat. Pro porovnání, 500 Kč není ani to, co byste dali za pronájem komerční zkušebny na jedno odpoledne. Také jsme dohledali, že tato výška příspěvku se drží na stejné hodnotě minimálně od roku 2012, kdy byly ceny na trhu oproti dnešku výrazně odlišné. Minulý týden ve středu 15. 5. 2024 jsem na schůzi představenstva klubu vystoupil s bodem Návrh na změnu výše členského příspěvku Hudebny. Na této schůzi proběhla tři čtvrtě hodiny dlouhá diskuze a projednávání všech zúčastněných členů, jehož výsledkem bylo hlasování představenstva klubu o usnesení ve znění „Představenstvo klubu Silicon Hill schvaluje navýšení členského příspěvku Hudebny na 1.000 Kč.“, které bylo přijato v poměru 10 – 0 – 1 (pro – proti – zdržel se). Pro ty z vás, které by to více zajímalo, můžete si přečíst zápis ze schůze představenstva (https://zapisy.sh.cvut.cz/). Protože představenstvo klubu nemůže rozhodovat o výši členských příspěvků, společně s delegátem klubu v parlamentu SU a předsedou klubu jsme včera (úterý 21. 5. 2024) přednesli tento návrh na zasedání parlamentu Studentské unie ČVUT, kde byl návrh schválen v poměru hlasů 13 – 0 – 0. Proto prosím počítejte s tím, že od následujícího platebního období (tj. od 1. 7. 2024) se výše členského příspěvku změní z 500 Kč na 1000 Kč za pololetí. V brzké době se tak budete moci těšit na nové a modernější vybavení jak na třech hudebnách, tak na našem skladě, které si můžete od nás po předchozí domluvě jako členové projektu půjčovat (viz body 18 a 19 našich pravidel https://hudebny.sh.cvut.cz/assets/docs/Pravidla_Hudebny_1.0.pdf). Příspěvky za první pololetí zaplacené do 30. 6. 2024 (v hodnotě 500 Kč) zůstávají platné do 30. 9. 2024. Někteří z vás možná vzhledem k těmto okolnostem své členství v projektu ukončí, ale věřím, že pro tato opatření určitě aspoň najdou pochopení. Myslím, že je vše shrnuto přehledně a jasně, ale když budete mít doplňující dotazy, nestyďte se zeptat. Jinak se mějte krásně a dělejte muziku takovou, jaká vás nejvíc baví!",
                    ),
                )
        }
        "post_with_instagram_link" {
            val postId = "pfbid02oVfUUntLMg1HrnKGjhgu27TQrLLKjR72GJQqBmxUpYtQuzf3NSte6w51DYBP5jQal"
            val body = downloadPost(client, pageIdSH, postId)
            FacebookPostParser.parsePost(body, pageIdSH, postId).also(::println) shouldBe
                Post(
                    id = postId, pageId = pageIdSH, publishedAt = LocalDateTime(2024, 5, 5, 10, 43).toInstant(UTC),
                    author = "Silicon Hill is with Blokové hry SH.",
                    description = "Tento čtvrtek se v rámci bonusové hry letošních Blokových her konala ranní jóga. 🤸 Více fotek a videí najdeš na našem Instagramu. 😉",
                    images = listOf("https://scontent-prg1-1.cdninstagram.com/v/t51.29350-15/441730885_335831969166368_4043422195989950892_n.webp?stp=dst-jpg&_nc_cat=104&ccb=1-7&_nc_sid=18de74&_nc_ohc=jv_Wgs4RUUcQ7kNvgGGUE-s&_nc_ht=scontent-prg1-1.cdninstagram.com&oh=00_AYCPoNTT76siOHc1hSsEVzjmSVdiYQf3tDpVdtd51k4TrA&oe=6677DDD9"),
                    links = listOf("https://www.instagram.com/p/C6lkSbis8xz/?igsh=MXY0b2w0c2dtMHE4eg%3D%3D"),
                    eventId = null,
                    references = null,
                )
        }
        "post_with_event" {
            val postId = "pfbid02NEq2vQdG8uVqGr1jR5AbYynHQEHmAfkP7P317cadaJis98GHerB5frS56Jsdv8VRl"
            val body = downloadPost(client, pageIdSH, postId)
            FacebookPostParser.parsePost(body, pageIdSH, postId).also(::println) shouldBe
                Post(
                    id = postId,
                    pageId = pageIdSH,
                    publishedAt = LocalDateTime(2024, 4, 29, 8, 58).toInstant(UTC),
                    author = "Silicon Hill",
                    description = "Už dnes se budou konat další blokové hry. Přijďte se k nám připojit v 20:00 před Blokem 8 na naši další vzrušující hru blokových her Vesmírné Podnikání💸! Přijít může každý, ať už jsi na hrách nikdy nebyl nebo jsi ostřílený veterán. Rádi vás tam všechny uvidíme 😁 Navíc byla vyhlášená další Bonusová hra, které se můžete zúčastnit již tento čtvrtek 2.5. od 5:30 mezi bloky 4 a 8. Více info: https://wiki.sh.cvut.cz/klub/blokove_hry/ls_2024",
                    images = listOf(),
                    links = listOf("https://wiki.sh.cvut.cz/klub/blokove_hry/ls_2024"),
                    eventId = "955412316203133",
                    references = null,
                )
        }
        "post_with_event_and_no_text" {
            val postId = "1163760511605627"
            val body = downloadPost(client, pageIdSU, postId)
            shouldThrow<ElementNotFoundException> { FacebookPostParser.parsePost(body, pageIdSH, postId) }
        }

        "event" {
            val eventId = "955412316203133"
            val body = downloadEvent(client, eventId)
            FacebookEventParser.parseEvent(body, eventId).also(::println) shouldBe
                Event(
                    id = eventId,
                    img = "https://static.xx.fbcdn.net/rsrc.php/v3/yV/r/oSlCYxcwUDb.png",
                    title = "5.hra: Vesmírné Podnikání💸",
                    description = "Přijďte se k nám připojit v pondělí 29.4. v 20:00 před Blokem 8 na naši další vzrušující hru blokových her Vesmírné Podnikání💸!\n\nPřijít může každý, ať už jsi na hrách nikdy nebyl nebo jsi ostřílený veterán. Rádi vás tam všechny uvidíme 😁\n\n\nProtože jsme na planetě Kepler-1649c prohráli mnoho peněz, tak jsme na tom teď po finanční stránce bídně. Na tiktoku (ano ve vesmíru je také tiktok, expanzi nezastavíš) jsme se dozvěděli že vydělávat si na sebe je jednoduché, stačí si koupit jediný finanční kurz. Tak jsme většinu zbylích peněz utratili aby jsme zjistili, že nejlepší způsob jak si vydělat je drop shipment. Kdy levně kupíme a za draho prodáme. Tak jsme vzali to málo co nám zbylo a vydali jsme se na cestu za finanční nezávislostí, aby jsme mohli pokračovat v cestě za vysněnou planetu.🛰️\n\nhttps://wiki.sh.cvut.cz/klub/blokove_hry/ls_2024",
                    dateTime = "Monday, April 29, 2024 at 8 PM CEST",
                    where = "Před blokem 8",
                )
        }
    },
)
