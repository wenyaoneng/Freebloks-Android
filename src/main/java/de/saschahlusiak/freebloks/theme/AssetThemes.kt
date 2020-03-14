package de.saschahlusiak.freebloks.theme

import android.content.Context
import de.saschahlusiak.freebloks.R

class AssetThemes : ThemeProvider {
    private val FloweryCloth = AssetTheme(
        R.string.theme_flowery_cloth,
        R.drawable.texture_table_1,
        "texture_table_1",
        "texture_table_cloth_1"
    )

    private val StripedCloth = AssetTheme(
        R.string.theme_striped_cloth,
        R.drawable.texture_table_2,
        "texture_table_2",
        "texture_table_cloth_2"
    )

    private val Wood = AssetTheme(
        R.string.theme_wood,
        R.drawable.texture_wood_fine,
        "texture_wood_fine",
        "texture_wood"
    )

    private val Metal = AssetTheme(
        R.string.theme_metal,
        R.drawable.texture_metal,
        "texture_metal"
    )

    private val Bricks = AssetTheme(
        R.string.theme_bricks,
        R.drawable.texture_bricks,
        "texture_bricks"
    )

    private val Carpet = AssetTheme(
        R.string.theme_carpet,
        R.drawable.texture_carpet_blue,
        "texture_carpet_blue"
    )
    private val Velvet = AssetTheme(
        R.string.theme_velvet,
        R.drawable.texture_velvet,
        "texture_velvet"
    )
    private val Grass = AssetTheme(
        R.string.theme_grass,
        R.drawable.texture_grass,
        "texture_grass"
    )

    override fun getAllThemes(context: Context): Collection<Theme> {
        return listOf(FloweryCloth, StripedCloth, Wood, Metal, Bricks, Carpet, Velvet, Grass)
    }
}