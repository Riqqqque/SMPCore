package me.rique.smpcore.npc;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.npc.GuideNpcManager.GuideNpcType;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.HologramTrait;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CitizensGuideBridge implements GuideNpcBridge, Listener {

    private static final String NPC_DATA_KEY = "smpcore_guide_npc";
    private static final String NPC_TYPE_DATA_KEY = "smpcore_guide_npc_type";
    private static final String DEALER_HITBOX_TAG = "smpcore_dealer_hitbox";
    private static final double CLOSE_LOOK_RANGE = 3.0D;
    private static final float DEALER_HITBOX_WIDTH = 2.4F;
    private static final float DEALER_HITBOX_HEIGHT = 2.25F;
    private static final SkinProfile DUELMASTER_SKIN = new SkinProfile(
        "SMPCassianFightmaster",
        "rTD6Ybj0FoRuIrxdngN3Nt0rSQCnOSVWmMLtEOo/pIRSBvlqgAC2TQnPJOsPUo5mNcgB11eka1GYvb/8pYcgqgNw54SuEbMvYf/unxjVa+m2Vnqyk8v5onU2KjLqASaltY203+m8Q1g4DsauymYXLdUkM1au+UyhlA5r27VAqlBa+BuBl6DC03YXcG3Dtc+abfv0zTKG51rGNAMSTTsRsZaJMLQMtIEq0Zg2w5Eh8o35pzAXWb1/pTtLh5btyTvslhA6/YkY1YCfolNlUsLWqiEkOQ6sVQSwitYQzrEL+x5DssPiORMmNfE7nutZcu9fzNZPTUZ9mJgvAndsQRJmh4NH4nCej905bv5bMmrxW1Sa6x7o1WUuA1VH3yPWB5vLNRP5krQBygNgPS1QKXN1OoEE9NcASH+n2P8mpxbDpvfz5hYIBxueLVMnSp9RIc68J28+mGHquTTYWYMkc4PFfIPubAs3P84QSoh/yLoasIkQs++3l2mBbkbtN/14sLqCjARRBH24dUHRxS5W8JbPXpDON4WTfm20hkSylo4il3AodlPhV0oEkc0vSOpSMQvFfpFW0WblHCI6xUxvOB3xPe0hUiZEIeQLV+MsM6Evgnm70T3MUDHgi8VBNIgo+efO/oIeytJsYdvNXM4VzSVcBXPwxBHXu7vE6WV6Nw+ZVq4=",
        "ewogICJ0aW1lc3RhbXAiIDogMTYyMzE0NTk4MzYwOSwKICAicHJvZmlsZUlkIiA6ICIyMWUzNjdkNzI1Y2Y0ZTNiYjI2OTJjNGEzMDBhNGRlYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJHZXlzZXJNQyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9jNjZkNzdhMWU5ZDA5NGQyODE4ZGIyNWE1YjJkNWViM2VmMDhkODUwYThlMTRjYmVhOTI2ZTAzYWEyNTExYjY4IgogICAgfQogIH0KfQ=="
    );
    private static final SkinProfile MIRA_SKIN = new SkinProfile(
        "SMPMiraGuide",
        "AtPS7B0GpndanpHTtveJw29R++0y03LLrcw/Nz4z8yFoh1QZDZzqEvjapeiAmu+qUq+nNjzgHZ1MnjI+O+8t6hgmDOKyRzsEkLH5bubizjwZ+KiNRCbraA3P"
            + "yRAqtpegIe7+K6QwL2UtsR40zSiheTlZDPOzevFuSvREyVLoB06xtO77n8asSargVwH3ViUyEBKE/WUNtRCr0KJUsV7RVcZivNRmSvf6o3b/f3CdolcWOnkB"
            + "5tTUNlc4zfa5JNNEsCQfsTwjOGUm73J/qzG+fteapmTyRDjRxE5LI8B0Mp2auTS0Bik8F605GKC3YS+vgidT+ZVRhn64SSmU2QOb6WtPIK8REZir3PF/jKlN"
            + "zsCu1F6quAHkBlSalblkQnpoW7Yw0sl670iB6VsQ/qiVsAp7HPk00rvQr3LOYqq8TLaKC1t905FWNmSLIRvCYhlLupRG1//JCYB9AGWOYYV/R0OjA2eoYyRh"
            + "GHOu7MvEOzQPa341gcvzSsBfARB3m3nsXbLpnO45A/Yfm/AqtR9/mOAO5vceB/8vO7Y5LdJI+XeTYrZmwkGFkbGTsLMyJRy4NWAos8r4oF5gsom1Tly2MDZG"
            + "fje0KOsCEJzabWvXSGNgnuKU/FS6oSrFmQrQcfHf1ExnwB0czdaofyIp2qAPPK0i98tsYPkWcfUxU7+ZBFI=",
        "ewogICJ0aW1lc3RhbXAiIDogMTY4NzI5NzY4MDkxMCwKICAicHJvZmlsZUlkIiA6ICI1ODkwNjAyNDYyMzE0ZGFjODM0NWQ3YjI4MmExZDI4ZiIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJXeW5uY3JhZnRHYW1pbmciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIg"
            + "OiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjFhYmMzODZlMjM0Mzc1ZWJhYTVkNTM4MDQyMzk3YzI2"
            + "MmQ0MTAzMWZiNjY1MWY0YjQ0YzkyOTc0ZDQ0ZmU2OCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAg"
            + "ICB9CiAgfQp9"
    );
    private static final SkinProfile VEYR_SKIN = new SkinProfile(
        "SMPVeyr",
        "gKYaXCDvbYyl5gt4PDEmLr+Gtwey/iLlt/SCmhztI9KP2a4B77He8cpfPI4q3CXT8vDGxJ9LM+byd2yl5Cww6tvqyFQtFjZxiRtpB8RMK9wGyEK7z1Dn7sZq"
            + "JgF6du5jeu4DtwlTrbE1l5BfbGb3SmpSUf9z3n3P9E5h3xJK4r+gHuqdNWfkp/F1xyRsZbK1abbMnLEJ7JInYXA308NMi/IMqR+sNRmdC4YhzTCT0GW6RgOz"
            + "hFNaArHLpLjMFdJY0MldTHOS96eWxUNAcRDimgGQFMU9jFq9QdwsZCB7cYNbIpHDfTz9Gjpnm+XiL+csDB7shLLCog0GMf7x3MPzLktzaJ2ZFjT4ydZ0kQk5"
            + "ItmFZN4oTVvebZ8WCPTAi8iywtx1ih1cOMgtMiMrVmVC16JuOUjEhRTtusA0X53zbwBNg4XUl+AXTvFzt9sL/mWewMSqSoC9OFUjT0WCX4DKdKzahjbsZuTh"
            + "XXa3CrH9r5d9E7jE4baYxtt3qhSGmRIBfOLvSNmPTS/lpLrX1qS6wHXqePDCN7CycvdCGKacCzzWyq+nSGfKxKxxiRaViJREC6BzDNQDU4RiAmVihGgU8T3G"
            + "VROnqXkNhAfrB8yWFo1O50QkTEUb3zMTt74ZQLvIg2hf1ymbUoPA49JNrnVVlivyytcXnTLgHM8+csy7d7U=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc4MzI0MTA1MTkyOCwKICAicHJvZmlsZUlkIiA6ICI4NTBjMDYyYWE5ODc0OGZhYWQwODY0YmRlZWU5ZmExYyIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJDb3JydXB0ZWQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAg"
            + "ICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDZlMTQ3Mjg5ZGE4ODBmZTIxODY3NDg5YWY3OGY2ZDUyYTA1YWM0"
            + "ZWUyNTc3OGNhODVlYzc0YzJhMTlkYzc5OSIKICAgIH0KICB9Cn0="
    );
    private static final SkinProfile DUNGEON_MASTER_SKIN = new SkinProfile(
        "SMPCoreDungeonMaster",
        "N60/2k+w/kjbjS2l/z45f1w4ovcBmbqZ1690GUbzylHdKV9CeeF8m7kBgu+1QevD4RKfNjfJU6DjjUpv7MWptoYgS2A76criSVwej+dVE0KVy1ggMbVLueup"
            + "N/lZh0iZfnD588FQs21//nvDuVFWYWQa+e7h0SstIjvLxBY6HRG9IDUrGnPlrzjNwbPfvD4sruOMr+3BgL9WPUQmdL7YJyws4zXBoSxRomN3bQm6lNuMoIlf"
            + "lYSFOKuG9YGoYsrWC8HWDlebDXIXw3YonA3HG82GM+14Z+ViDEo15kBLOOzQ4wfwGQ8ZC6FqAFa379EbKnGAUM8Ys+6KmF/TEstTIFP0iYIo4oZyWdzYg9mD"
            + "KnKUthWVoq3LTCWyB0B3rlgonFF9zi9RMKuwuCHQcf3j6CzBtbq0CKvCoSNOIdejUQ69/LhO4PPofBHXRyyoJWQzrDYwEveZdi3dhV82zsihjlMM7xodZRaS"
            + "U6SWNAR7TilSKXv3XoEDChBIugnACZChNijgC9Ttzbcoq5Ayy+O5PCyZxaJ3Wxxt6fDfEMrAYzzUWYnVwdXl3uInK5JDaKrMOxonckt85CGY4O+pZWUx/FXb"
            + "izXyMXpdL2OxyA00Qc+WsnmeLi2NUto0su8cEB++9BVQYAbCPFOQarMAG2E2pYUGu86iOpGcNt0ikjhG3u4=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc2OTExODk5MzM2MiwKICAicHJvZmlsZUlkIiA6ICI1NzI1YWJkMzY4MGI0MWVlOWJjMzhhNmUyM2M5OGNiMiIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJBYmVuZHJlZ2VuXyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsK"
            + "ICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9hOWVhYTBiMGYzZTQwMzkxNTBhMDVlNDVjYzNmMWJhOTVkZDcy"
            + "MmFhOTU4NmI3MzlhNzhjMTY1NjMwOGQxZTBhIgogICAgfQogIH0KfQ=="
    );
    private static final SkinProfile MAYOR_SKIN = new SkinProfile(
        "SMPMayorBahSheep",
        "lTgl/S27QP/eZCUAZrBfZFJJ3YuXGlptMTVo1AgGuDDYrQVNhcN+w34VRXSC/V/H6pTbrj+QTkyh1Z6/1MjafKsl+dHDFEW34l3kSnxWwinQ3kvqMq/Oarq9"
            + "LQKMKTyyGobUNOuOq/Zmpwy54JNNsPJlNB2/uF6gpznfdIcXs/3WxZrAbuJchomZ33h59vFB7WUZ/QUERDlRPIYdgF6pex7JDiVlSjLqnqMoPeRk60kEgntf"
            + "PUSMhvRTtgl/n0X84rlDqblqwvh3B2FY4ptOfzJ1eLvgJbRn1Ou2tJK/CwGVGx1oCEPo9DVn1utlX9YzwUnRu8TiTs8/LswXOwmNAicaM1blTduizH+4LdGi"
            + "SUw+P+YBKqTVK99Y7lpK/BsSCkW9f+YdU99Z0z3FqntQl68qdteK4dIlxbYOXxll/L4Yfpi3ppGPl0jqVjuoIifKFhVauksIwtTZrhnSOhRy4PLbI1rdLT6p"
            + "uc5k75UGVJot7XyBoNJ/hQz352WFnYI/zYmU15D/7Z+kmKo5e3L8fSkf1iG/BeXgVVolzFo3+Ng1l3CdAxt7x2PIrb1uy5lM5fv+erF1+5ZVD1TwwlepBfmY"
            + "+D5rHmcwdPLmqinxaqM2IpLgxJFkZW8RR4Fmro83UsNf+3yFqjs22N6D9RSYGvBLD2JpXNZ1r2VSx0zWZ/U=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc4MzI0MTg4NDU0MSwKICAicHJvZmlsZUlkIiA6ICI0N2U2MjJjNmE1OWU0NmNhOWU3OGNjYzE1ZDliNzhhZiIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJBbnRpbGFmTVgiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAg"
            + "ICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzliNDQwZDk4NGM3OGI1NWMxZjZiMzg5NDMxNmVmZjFkZjc1NWRi"
            + "ZGNkMzU0ZmNjZTk1YWM0ZWQxYTRiZGJlOCIKICAgIH0KICB9Cn0="
    );
    private static final SkinProfile ORIN_SKIN = new SkinProfile(
        "SMPOrinArtificer",
        "ObIylEWY4VYgBVrvNikmV1W75U71QQYPwh+uLgkOFur73Mp3BY1rVjqfs+kP9LsaUn+NmxzkJM3zbGA2DZFQ80QnrEHUiX7K0fgnil1hWpp7NPyXALVUpOoz"
            + "5G+DRlIvc+ySZRRAQ2UkEpqV3yVp/0slbG+H40PMTH++xxTzwMWvibzDHKR/g1YToYVOx/t4TbUGqt3y0b8VoTydihp4uB/789Hz3jpj1dz1ZqtlwH14bNoO"
            + "kVv7/atpcgHO4sqo+XBq7QHFJ8OU/1Np6EbQld4yuLUXW3zwv/jjygOQ29IIYrSJR/fYjQTXVaSucUFBsw2FizuZAA0eWeEHhf95YEgmCSFkQzFUGD/hrjms"
            + "mYKKa/Y0OCHGmQs5+dhUo1JUScGwFSxyBCfn1J2AQFByu3GtaVeI0VULKcWMK8P47qqmS3dV71gxWLlz27XLuVDIIbpsXECkJmlxZwEWf8MQX/UAaj4yy0jz"
            + "xDo4RyUqCv2adqvuPNkMG6CgyLj++apS8uwoVUPqNJyI5Oija4TBmSsnEx+DCNVH46mQrgrfsQzYBlkTXcmQuQTCky6PhlEAHniSVHOtl0S+JqFNj5RnDj2V"
            + "gq/UuiymPd7b6ZKgvl3EFV+KYESNILvtLXl58pxYpNmnpvIbcP5fFsGk8iE4ORseUu6aVA94CcvoVU1K0ns=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc4MDU4ODM5MTIwMSwKICAicHJvZmlsZUlkIiA6ICI4OGMyOGYwOTY3MjU0NmNkYTg3ZjVhMTc0ODU0MzExYiIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJOaXRlQ2F2ZSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAg"
            + "ICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS80YTYxNTkxZTU0NjhhY2Y4YzkyM2Q1N2M4MmVhZmM1MmIyY2ZjZDU2"
            + "YzQzMzM1ZTIwMDM2MzFiODY2MTg5NDU5IgogICAgfQogIH0KfQ=="
    );
    private static final SkinProfile GOBLIN_HUNTER_SKIN = new SkinProfile(
        "SMPGoblinHunter",
        "UHGvEnSJ2TmXS+s4BPh0InG230iAOGv5cYJ/WmlZK7xvESpSBF2FjV8rGIxrtaYMwUb41WdyGsQjppsTcxXztIf/dR+mXS0NJBRD/5aJRxQzDSrY4b+LwJFg"
            + "4B1srywSubnj9hHtMJdA7kh1yWEidzv1462DOj8GyTaP6+NKk8xVQ0GRmzuKbOl2g5oC3bGEyHZ1Mb/Vbx2j4lxMFYP0FgtEtnFJHGk0ZXleip732+zgToOL"
            + "QcD4Yd7cHSldvElTNdKiq+izZZvLSM8IWjE4nQRGvubzCKDBd23GdKTcaol/lXJy4jc8q2dnGrxGVmPhJF1gaUNntFoX7mKYhyG+H3nt050fv2q6WD404Tit"
            + "zSH8X35WhKTgtiMplDj61rFnKV0U5KigKhU+wwRTK94x9u8sYPJKvWtjS0QQS3ahxSSja6Z6uQjUNgRB/TGKyL2Nz2ANlMMLRIaCN1LSd41Ao/krkayZgtlO"
            + "a6e5TTpswZmIkSb36fwSF9Sce3/4hUk+rfNZ2nhCeI9QmfJ4wvEgqKVwXU4ehir+5ILrwHUDBM/W9q1GUsCLHP/a5eBSLF2kp3q3y8twuuK17QWsFR9pUDAD"
            + "tnfpJf9VFYLutnUZ+/SPbIuSemKGpSA7+CzyMiy91ZtEcowwMp25/A2412zkRyLsgbSX/5uyUyrDDf4hzAo=",
        "ewogICJ0aW1lc3RhbXAiIDogMTYyMzMxMzE1MjE0NywKICAicHJvZmlsZUlkIiA6ICJiMGQ0YjI4YmMxZDc0ODg5YWYwZTg2NjFjZWU5NmFhYiIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJNaW5lU2tpbl9vcmciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7"
            + "CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTA5M2U2MjI1NmJhZTU1M2JjMmQ0ZDZiYWFjNjYzNzExNTVh"
            + "YjI2ZjQxNWI5MmNlN2QyMjI1ZmJkM2IyNDBiNyIKICAgIH0KICB9Cn0="
    );
    private static final SkinProfile BOSSBROKER_SKIN = new SkinProfile(
        "SMPMogrikBossbroker",
        "Lef9pu/uqgTB2GHiofG8IKWxb6PR6n2msnKddPu0cOJwM8aV4iKtll+IQh1LVXtR1deBx6c2LGssBEdDckXBQ6sCUpeRWiu0jzQOmWQPPeuIZ5LNxViztC44"
            + "aB43PhvygiQsBUn+CFmeX8l/IYzkguvuWphHD1iqexUjAloReG2Pbk6GvQlRsve1tF+MlLmwFpM+d+3SUcxRcGT7gRkWmBDAifVSV/ZJiFQSBgzjB4NXTONw"
            + "fzqMYrWMULR/zAMAoEj82eLZKQ1U/Gfd5br+e+E3FuIJYLV9EQCld9c4tW4X0qTrYktoFGwtZdFPObaBBgVRv5MxRpvtcZn5xwY6nBUIIv1SMIf6Y9nQ5Cqy"
            + "6h7PSKgZCr8FE41Nk7d5/PTylUstY5MZ6B7WL3MowTNZ7yxoouXBcvqhxegIVuuDoSZDmj8w9rkS4SR0LzHJA/Xl47N4VUHDMYPdGpja8CjXKGPs/dO8ukt/"
            + "78GracdMGUYoTPVQ7LfSBGCx8NoWNdVWrey9XlM6CZ7SEefZdxK+dBdF7J5gfF9SkJ85F4qq4Yil+EO2DtcTTX4B9U3z5i1t8s7iu1B/cih/Qz45GjnccG/y"
            + "PXuquApuByMsaphfUOGlCJyFTYG50/kLoA886EApzdqaGI9KI+jjsf5uJRumodX0x+3DURc0HByfIZPlVuE=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc1OTg3ODg1MTAzOCwKICAicHJvZmlsZUlkIiA6ICJlODE1MGY1MjlmZGU0YzdkYjI3OTAyZjJjNmU3NTc5ZCIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJCcmFkQm90XzIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAg"
            + "ICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTQzZjNkNjFlZjNiNDQzM2M5ZWZlZWJjYTk3ZGU3NWEzNjc3Y2Vk"
            + "MzBhMzRkMDE1ZTBiYmY3MzcyNzJjZGFiOSIKICAgIH0KICB9Cn0="
    );
    private static final SkinProfile ROOK_SKIN = new SkinProfile(
        "SMPRookTavernWorker",
        "NQbxCeTvvV7AdSS1AjAt1gSpIJmlagAt+ViXfZurAotvW6Mkvbgh//5hlzcEfbIIplYMfBehAij889nRoEmmQpUREUYwk70AcbXk6r7uGO+9SfnoZY+8fB/6"
            + "xPYD8+7Tq6JvJznKBWq8A3gX2z0lYBrBl1LDfcbZuFYJdTSAm1ENTDoW/8l8bSBuP94xSuOsNIfsllWyk9yh2y0ndVX5GNqDMLzgvk/btkw/RvEakLH4MRZy"
            + "9E5kdi8ootsEJaOmvNOaTC9AAbDrlvcFgxGCxtGLFSryb+8FxfCp5p/jcJg8tUT4hbcvmpQkXW5dX6nTZaSV9GySbYPANCZxny1z75niwm4xCAQl7qxvlnN3"
            + "IC6sXdLUmJxB6fOUq1SRpFfJtRP3R8jNn1TgX1PqTc7AfECkD+uLmRsrr5yLyrPvVQonAO8Z1YsclAHF02fk1vsbY9ukolcvONbuKuZwa/nXtIEJVZss7uNp"
            + "6tJHjbcOdtQTT+Lxokx7In7FiMS466v+HFTEfFxa/EP9PomcwYZp8g9cVahDUzycPPtCVteRdpx3cEbonb6LZqTZOjRPsLaPgHcUnI4GQOyjg4R2ZvOp7CW5"
            + "Auq/GsrfVZi1BVy5+z9eePhU0j9PN5yX97iw2k7IlXRmok3P2fVcAgjoWMLEsm84ab38pubT8b4fsoalhcU=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc3MDkxODg1OTg0MSwKICAicHJvZmlsZUlkIiA6ICI1MzE4YWJhNDJiMTk0ODNiODFiMWY2N2Y1ODVjNDdkNSIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJocHllZiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAg"
            + "InVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9iYzJiYWZlYTE4NmQzZWY3YzQ0YTI4YTIzNjRkNWI3MTQ0MzdlNTk1NjQw"
            + "NDQ0YjE1Y2VhYTlhOTY0OWExYWU2IgogICAgfQogIH0KfQ=="
    );
    private static final SkinProfile BRAM_SKIN = new SkinProfile(
        "SMPBramBrewmaster",
        "iEW6GqTDVHrEBxHI+A9nCtsTM+2uJ5OCOYVU4V61Qbn5ItIIQ8SFeKOVYdSWwBDE/GMReYtlxh+6R97crzdly10IWOYleqlWwMXUA+RjKASRYRbm+gkA2+Pi"
            + "BusIuGEwwySELWZVfseJa2SBkQh8vjKN0jjI941UkIhkeFTCs6t7jEH4K5qdUUSG0H2V9I5hcr0EDbOtLRQgcu7NUrLW3422T2lv9z3JnP9QXSwGGHlCzKDo"
            + "JpAPhZEWvDtqkw/KomH027EEqLAf+5sC5Xk/DoAoih8EHyqRc9Qfu0AwLAT1i2qix/CjgIebSCMTBgpRDn3mJLu1osolLhIAwYKKFw++qzskWXx4mOlFjvH5"
            + "k3guGe+LqhKJqG0fvN1a7oDG1ogKTQ6Drag9RYbv434LBgPTQLJvgkSmMkAe/lAmmsYmwjeqgnab1cOKomzKd+2SFUexGsEKcKi2t0gZ8tV2uIG9C4P0cJBX"
            + "0tjHW7pQU+NQiBQEqx8k0twe6+5Huf4mhedHA0WQ1Uwh5OPm2xYux69zgBZdL0Xzd0VF0B0WHoFUc1GNk4gxlwm1H5rpzvg/DnW/BXI4Jjj6KsMaoVdbtMsS"
            + "ed99qFOyNHwR9VBluy5dgwlqayBPJ/z9Ruo4AXZTUohUdYE4zsQmJKovVReCY5RjExkOoSpGsFD6CUez21I=",
        "ewogICJ0aW1lc3RhbXAiIDogMTcxNjgzNTc2NzE2MSwKICAicHJvZmlsZUlkIiA6ICI2Mjk5YzhlYzkxM2I0MzMyYjZiMDJhOTFmNDk2MzJhYiIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJIaW5hTGluayIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAg"
            + "ICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9hZDBmYTJlZWE2OWZkM2M1YTg2YjJlMzU4MGZjYTgzMmY5NGRiOTJk"
            + "MzZiYWMyNjY1YmM1YmM2NzQ3NDU3ZjQ4IgogICAgfQogIH0KfQ=="
    );
    private static final SkinProfile DEALER_SKIN = new SkinProfile(
        "SMPSilasDealer",
        "MKppAcxpXXZPbm8Z3UmOFEOMWq5YWpfiM4w4X7MA9I0Y1vZLNopYBrS4tqo4COxWj+iRMCUuCSl9Hx9NFW7/sD8baDnNIdtpDBonOhKToFYALqES5DQsEt8V"
            + "8MgitNTKrBwN9zWb9WDe4lzp9U9ITDRxxv2R6NcMu5y0GgYBV05Qw+iEgfB+ulxJ0Bbad3oXMj/zgfRjDuROp/GIxGuoYEqJp8TcJiWuoDTiOQUoKVMPRdgg"
            + "fCd63CvtfERjLwU33nCw9S6GHKIw2m/bzXJrvuyQ3d3XaaMUKWrhpuqWlN8xhmVVTt+iBQsqoCivYw5xNQxkDTfR6/WASiNbpEK/huCPDq1qmtN3cVe0G/7w"
            + "r0D6B3jWT6pOILM8jt/obTzK0Rp0Xyra/CW2pxTfu+3zxIWRszpeMLNa6PGrNqAnv8cPVA913EhZJdzJkmN9tMKcp6AfxMQgbEWkrIDbkouZH/7xGjo5jvkS"
            + "OHJL87SJfWZDuSqKnauoqmcSF5v2saykFom63WYtfzF8go/2FKxxJwzph4DH0874R776AlakR96Fpw++4Y4C+77JZnJB+42Kya2BqH4Q/5XvpJRIkuh3IiHo"
            + "MNwPNrKnJjIPTEEqwPwyDq5IcHdsnTjFirDNVp06ZmiM0yjXJX/SYE7SqQRnffYGYugxlnsYziQZVJSBDCY=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc3MjgyNjE3NTcyOSwKICAicHJvZmlsZUlkIiA6ICI3ZGExYjViODdhMTg0NzgyOTQ2ZjZiYTdhMmNiMDhlMiIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJSeWt1bk9jYXJhIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewog"
            + "ICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzZhYTdlNjJiYWNiZTI4MjM3MGVkZDA1YzE3NzZjMzE5YTNiNTgy"
            + "MjNiZTMyNzQwNzk5NDJmMzMxM2FkMWY4MGIiCiAgICB9CiAgfQp9"
    );
    private static final SkinProfile ROULETTE_CROUPIER_SKIN = new SkinProfile(
        "SMPRennCroupier",
        "j9/wsmvYhhIQAxdgNog+ErxppQGhtEn5dZQRlvKHxQCRtSts4WErzdiDYa+1kT3CnFxX+Q5I354/uqoRxdVk1ojSROL5f4vBIE8f1mxM54hUzeyXgBpCobX"
            + "U/QjuIVBJoRVzWwK3iTVWJbB7uHzn0bGdv64Tkynyo/wZpYGpf+SyZEXOgASQULfJBJHYwpdfniI3cyTVb8EKnGFj+Rpwx8tF0I7w6UDIYn6SlqEL0zj2RLZ9q"
            + "GYrW8RJyCtuqq9RHuEIw/UDtmJr6HGTL3Ap8ottgbGd05J46OYM6vEsMz4hF1n4eOSMgQzThE8DYBF0WVKLa4M7YwnVKPfgalyA6/JBYivwNT9Mo1OVyHQODMc"
            + "Us3XDM241GkkMX88PR4giXEpYfVkSAPnXV6kAOVbCyQYUxZ823/Fw5B/QlXuDsBNhkRsK2E7EzGMcpGwBlU/pphjSaDiaVRua5jeIovwC3vPNMQHWNIhQXbJxu"
            + "Ju/32e03JyZVtVVcVxg/rcZ/d7fojKQ0H1Sp4yS729K+VMRysyz6yYWA1vorpILcVbdxUL/LVNH76wOOXYSoOdCFZiCjoeCJfykU3HLGpfoHOmnRThr0TfxxI+"
            + "X3jVDSgsgLRcl8G//B3TVqjSCsa1DlKcbprtqTVxBFIBhvfbWUJ9Yl5hL13BZU9H1DE4CZM6Sm6k=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc4NDM5NDU2MTY2NiwKICAicHJvZmlsZUlkIiA6ICJiZjExMTQyMDI2YmI0NDQyYjllY2JhNDQ3Nzc0MzFjOSIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJUaGUyMURlYWxlciIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsK"
            + "ICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS80ZTVkY2M2NDlmMGQ1OTkwMTU0M2VkYmIzZWM0ZDJlYjc1NGZl"
            + "ZDM5ZWJhYzg0Y2Y1YmFhOTBmOTQ3NTZhYjBlIgogICAgfQogIH0KfQ=="
    );
    private static final SkinProfile MINER_SKIN = new SkinProfile(
        "SMPTorrenMiner",
        "srFCP2xj9QeEdpwGQ2rbYFRzX8l0mWxhtiBJ3V1iSIdohmoiXNOCQT+0m9MDm+snywe2wag0JNsouBbmNOTmQvGjLMbYpTR8GFWhDTPhfzMLL+FonFXxZDCF"
            + "yYgYbmimFMXNK+gXCWgzD8UUfDpmKOa1SRc7ZI6RbwJ1tZS1u/mPf/qF7LNIxKXEicaSi/CxD304JWUWostR8VMDRrAyIlLD1KdYNzFFggEWn2yS/Jftaoy1"
            + "gUbMDWLzNywRTMyukrMwrj4FG7e+sm6rLpT+soRrVd4x/ojBBMDaITbJT9aBfXpNf+bTdX3dR9rJPEvvbtc+Xl35yJo4SKCoFRt3qAI6qultCcTjJGgBEjqH"
            + "V3g9lBX3P3xd178eui+uu978XIgM97n44X3BT4NqfJ8xpdcp7DQa2jkzpzF/JeC1OeVxnoD49ivNfiiPXEKYJJjv3rcLjXLL7pr63XwlIT5wCU243X9Hq0CZ"
            + "ANitn/b6V4KjXz4KFXQgyDxUJUyXKLGWMNYeNhCYl9QlQqn/d8TxpoclC9V19mloqD+liwdYZjZ9jf+fcOZBwh7PtvOqEN36mZ2c0cRrIaMQB1qwgR6dE5yb"
            + "7I8oKWT7ob+qr5cL9Bloqg1oRIt7BqjJj7IEbhayBqamMVmeJ64KylbszTLPCxetLJUDxJLSHkqQHJjMrMo=",
        "ewogICJ0aW1lc3RhbXAiIDogMTY2MjgzMjQ4OTE4NCwKICAicHJvZmlsZUlkIiA6ICI1N2I0MTZlNjJjZGE0MTAzOTRiNzZkNmNkNDA3MjFiOSIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJSME1CSUVTIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAg"
            + "ICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2EyMzZiMGU2M2VjYmJlMmEwMDkwZTRiZDRmMDQzZDM2YjYwNjhkMjVi"
            + "Yjk4MTM4OTc2NTQ1MGQ4ZDdlZTZkOGMiCiAgICB9CiAgfQp9"
    );
    private static final SkinProfile FARMER_SKIN = new SkinProfile(
        "SMPRowanFarmer",
        "ybl9ysx/W67Ub895p1JDpGtKU49UlLgOMndwQCrXeNxZM8Vrwxr/ZJJGinHH5IEuTH/11Ubpc/7yLQCzTBNCUdZk7XPMfYlqKnEnAw2Gri/Nf0HcnXCMNnFD" +
            "Flni88op5evCZY7JbOcqPSlUXYKS1nCkfMaPSVSZfkA4oxkBMl72vCj0Q138a3wTl3vksasBfgiFVf2/HyPO8GngY3prCW2kPf/2Vf0oKvYueFVaA1bnf152" +
            "sFEtHJ1pwzgNE33yWS7wm61LgAptggGg46sS1A8irfwP8LHITf3gdBeyctPrQw/Pqnh5RpNZnpseGduCK5zSYJY5zD+BEL0TJ5qkOIMWAtB1pfFpgjn54odY" +
            "Wd9WkY5M89XAtW3G5zcoJ3hf8aJBKCBFwFuvYz2thEoDYHYj1bYE5RI4NU0H3vJ01eZ7qrkz+Dbkik4kIZsuzFyX8K9ONr1c6GqJoxcUi5TfoFjSpozhm/Fp" +
            "7c85VapZNMqEVENrBts+lNRUlnQmeIafy5J2X1aaenh2BErvV8rxp5VSKrEmoOI9D8FbgZWxBy9siir0wuzbjkuyIeeKF+TCqgfky97kXeK49Aw0xehkST1z" +
            "0iyU9/XtPb1xSvbEAoYIAopfzwnqjapTWXnz+L/q9Q5XBZS3dR9Kc3Vuk2z+gRNLQdGFyE9V/RvXuMTQRcQ=",
        "ewogICJ0aW1lc3RhbXAiIDogMTYxOTI1Nzg2MTY0OCwKICAicHJvZmlsZUlkIiA6ICI3MzgyZGRmYmU0ODU0NTVjODI1ZjkwMGY4OGZkMzJmOCIsCiAgInBy" +
            "b2ZpbGVOYW1lIiA6ICJJb3lhbCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAg" +
            "InVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mODMzY2NiYzI2ZDA0MDljZGQ3NGM1MzI3YjdhNDhmNmZkZTIyNmQ4NDMx" +
            "Mjk1MTE2ZDM1Zjk1ZDJiNzY5YmZmIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="
    );
    private static final SkinProfile FISHER_SKIN = new SkinProfile(
        "SMPCorinFisher",
        "npuR/m5L190U3WrZhebMQWJRmHmZx4xj1fKufgnD93paBASvNAGcPE02gPo3nvTABbpwJo9GHyvZavdz5AcF2OghFzVKR582Dy5RT9F3IwgJFo+Pw3LnLE8O"
            + "iJ938BL4aAQlcdLeo5uaBd5d+QuZTZZ+eG7JED2UHEmkhEVPA5rTXA7VjTrltZ8FFvsQnlyyykykTu2K+kJK6JOeK7wFaV4lIJ2J5dvvqddTyCgj4e24zEK2"
            + "eh7LrneJZVwtmdEiwfobGoReaxN4Iw8CooVoDXakpD8n+MpgyHim7WfVo+dk/cPVUy62aneXprKFEdZapwg7Moh4jgVdPbPtfk/jNaV4r7lg7PIlRu/hLuD3"
            + "NsJsckuEG/rk6LwgZKqEv7xLSPfJZs+hkQrb16Q/El2tqQhQTEVKthoz0jOhJkyh3uM9+3jvQwuafYHMYcYKYR7sGMBR4Hdzrb2ugATifRONEgQEubsygWDX"
            + "Jdrvp41V2qrhUnUbjJS3WYXaa3evGIDP34Wl7wH53iIgMz/Czi0Zudw+WkRbFLSkdJDWG0gZW3SCcjSTFKCJBqe594A5G7KaylL3PzriH3WVIIeqWVwyZQmJ"
            + "MhYLvSrrMds1goL8N8/MsXCIBfHCSYOtL4gjIacIY1eg39osOpjTTIMEb/zdhaHGCyu7eV1oUMuizVgU5EQ=",
        "ewogICJ0aW1lc3RhbXAiIDogMTYxMjI4NzU0MDI5MywKICAicHJvZmlsZUlkIiA6ICJiMGQ3MzJmZTAwZjc0MDdlOWU3Zjc0NjMwMWNkOThjYSIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJPUHBscyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAg"
            + "InVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS80MzBjMzhlNWY0NjA0Y2ZiZmU5YTM2YTE1ZWFkMzIzYmYwZjUzMzkxZjM5"
            + "NjQwZmFiMzBlYWJkNzhiNTg5OTM3IgogICAgfQogIH0KfQ=="
    );
    private static final SkinProfile OVERSEER_SKIN = new SkinProfile(
        "SMPVeilOverseer",
        "BwtILASxiSs5ANIzv6Rz7Ggn1pu2Lut4EwS5VF1YNY7g7+RGb2mwn5OvQtnDk3G//18HlBERQdCFWongfpei0aJg5FfsuQTBA5C8KXvfTA0Hqirj7cUwptSA"
            + "ZO2qnPdJ1qjz6ZFUjyRAYgbqYHffdU/FQsqA+vbsyKkmv/R5RCpXZuN6wM7a2vB9okEe/3BeA/7kg99cSEvv6R38bIqB0rcwY7IBJniHgn/tz/vLcPxtzkxC"
            + "svS5Bi6toBYuYLsVn1ghZLe73mFOIntMOG5klgvzynyRh7YolhfhIXniNHMC4DedDeiBTA74AyhM2hQdp5he+nUzTQePAHQti1NemsiKuJ4NYp9mXIl1Rnnb"
            + "RCSKVPLCyrr4PPJdWtzuLKbAACkTsQVvLJh7vGzOkSYdYVdXc1bg5zOu3WwVtQbvFaBEpyERP73RnDQ8vUWZOvqmdH84CSyK3mrUE7NDmHbZyxQv3h6C/Q6e"
            + "XcT99nqlxQ9T20+D4LqBvgXtfW2/C2DALfHaoLyRvzm4p6Yo4LV90MMbtCkr1QTBMoRHe0SnMxgBgmR6WH975tVMwIxpsMhxO75+FoFgtn0tDE7nReNTF9aM"
            + "PaUzInvBJCuqWSx6bndpC4uP8Pxss8NDNcUadlk5DpQLldDQFVvDCZ26TZS6954x6RDDW2IBjfIUjrC97mU=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc3NjgyOTEzMDM3MSwKICAicHJvZmlsZUlkIiA6ICJhNjJmNGM0MWY5Y2M0ZDRhOGEyZjFkY2NjZjZkZmE5NyIsCiAgInBy"
            + "b2ZpbGVOYW1lIiA6ICJCMDBLWSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAg"
            + "InVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9hNDEzYjQwZDE1ODNmNTZiNWJlOTk4YWU1ZWZjNGZhMmZjMDdkZjkzNDMw"
            + "YWMxZmVmODUyZGE0ZjkxZDllOGMwIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="
    );
    private static final SkinProfile BEASTWARDEN_SKIN = new SkinProfile(
        "SMPKaelDragonTamer",
        "u5K553tIhO/rmsk7PKLc+EgFNF1jWCbAaX2KipepR4YOnj07vxKV9frEPzuOzLvC8LUcScu7Xot1zuUJwWValGkyRY4Ueqp78zssf8s0klVA/nJjmC90dfr2Vf/vuJRcC0v76mqUn3CQfhcF+tuugM69V/Kbo0tpsWvly1/6cGHoF1SkSbSjUIIlNhPqlDOBxsiDSIN1LLiOG/Orjr5O3fox1RxlITCZZFsYTKhl2QfnANcNBYPq8oCMt/vfK4iiMStHzsqb0J6he5R66mhuLwKcIPm4CPFZVbQFrpceDL/BQL0sRtRyP+XB68ACjkPALfDFthL7PBlws9k4fbHXSGAyGFCIaWHWjQj0O0jlnx7uteCYaOB3jF01lyKvHdBTJAEA4C+9fXZPJbUv3SQS+YCvpu2F6hWGm7kpog0tVloATgvKE2mBg2pXJxl5sT7fLCvdtG4w/A9K6vcwADGuDmL26K1/vNMn9Mr95dqajvPgFM9KsJui662shfEfNOV0EenxUsGjOGMwv8W1OZ+PwODsqHJuLkqJnV+BzzFfZWbyPxtbHMpRVtrtm42suXotCg5lNgEhDbsmscgRafLxYIZdRqssbSpy175IcMRDn2Uk27v3GJw01hTsStMwL6hGBRfr27MeHlH+q4KaujRnvrmHlN7ZMCEeQQyYx33DS04=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc4NDI3NTk0NjQwNywKICAicHJvZmlsZUlkIiA6ICIzNDY4Y2VjMWFlOTY0YWRmYWQyNjEzMGEwZGQ0NjRkYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJzdXJlZWxta18iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjY1OWY3YTA3ZDlhYmFiZjc5YjI3NTE2NTJlYjMwNDNhMTQwNTZjMmM0N2RhYTYwYTE3NzFlOGNkNGMzZDY2YSIKICAgIH0KICB9Cn0="
    );
    private static final SkinProfile TOWN_MASON_SKIN = new SkinProfile(
        "SMPTownMason",
        "HIFRq1SaJdoBgaNEblcB1cUVCo1WSIjRujVy0Zw6YgE30FqCVivpqR6zeYG+xs21VRMupGIhcOUmOnPQ6KjnbxW1Ebx10aDefqW8yYFuC+W0fFO5ZoIIgH9N4+pU6+Kz6hLNDjlzPZWomuVsinXUEvxX2Oa7hZaYpz9pPXmzPzVh/tyH3j08o8H+DQxo4D4wbBklO7fnyGxzPOTCZqo96JSkBWvZe7LikLQ3zoj4Vs+OkC6BWuO77t589w4SsyThTgYC/hN3LgCl6HaC5/bvqx4QEvzLc5tWeMTXufPpkg6KaHvOz6cBXnWqbpRorLudxAqY/vxDt+s0G1hvDaX2MziOUplFU+4RxTyEK3qRDx6pVJPax4TLoA97z3LHHzT0l2I0LhBVTJT5qJ2Nh+bdKKEuPoLIXDznhvTb/8NimthVOJPOUMSqzm83rG4WIvz57m79T9nSyWzBL4YHyAwWfo8m0AMduRwz1g+roxpDZ+Eir36teL1CuCGZp+OjbL2IKiqFjwnqEs1uFUUR/oXKJnWTnk2bE/97YuJ+41QapUsW+2q/R3RP7OpgCs0fm2XHayQlGwZ1Khl+/cXfcdWWipp+WsG/W/gGPQU6TMBbUCiOBmvTTNwhP09MCc4wbCvDILffUhKUXbunEvr4Gry0skN33XhotZyaJOM0uuLYBnI=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc3OTk4MzQ1MTk1NiwKICAicHJvZmlsZUlkIiA6ICJlYTA4ZjhlZTdiOTg0YmFlYWM3N2JhYzk3ZWVkYzE4NSIsCiAgInByb2ZpbGVOYW1lIiA6ICJXYXlkZXJUTSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS80Y2JmYjk4YjBmZGEzODVkNTk0NzJkYjc0NzZjOGI3ZmU2MTE5OTVmYTdkNTMxYWFmYzgzZWM5NDE2N2QzM2Q4IgogICAgfQogIH0KfQ=="
    );
    private static final SkinProfile TOWN_BAKER_SKIN = new SkinProfile(
        "SMPTownBaker",
        "in5EuC3YmE7xnsl/vf4BARzctZeh/ASCFekn5xxu3xZYAmQFA0AqFW9UbVwOtHgtaz0+IafRvVOYsTbKhikjoEA+vwis1xZpEYt4+CMeXI4WixWSIuPBPRQf" +
            "bCcF7zw/atUqQrrpwHH3YPYXWavq0Ze3ZnzrpjYZhSNzVOSrIMaDXfYhmkA2QdjvBPHem1Zdpuu/gbKdQfKBy40c98v8+AjiJzNT+87AckTHaX1/b1asSk0q" +
            "93D/inslPnF6gNYxEM5Ak/mlSoWQAF91ZulS+nF453zF/QEWtAxtBOy5Q2tDOMrM6yrlfXWKOFfbchu5QTAoGo28irCPUxMtytE/olcXq3SPtZL4jxfGcUTV" +
            "4TLX7ed+ibyJmlKHkaD0fnaFTxTtcShgy2PjUKs59jEyWE6GRzZhDZQxmDNOKD6as8bR3GNfm1ZnCbfLT5xW3h8yRpnCdygssqv0s/YTAxZ2OmtBN4Aps2wi" +
            "DLGxVmn3r0E1hm7onRn1MyBmAJnNSMROa47wRbtG3EYtu4LkXMpCvn+kiS2U3nt9hAwxzvanfT0L+6zphts/rGtQclGXlksgmYbZ5Ohiqxg0vZYOfA6De+t7" +
            "TCQ6zKwpOzNqojAx5MvVOAsPDXjLg8GA9odKJxziIiJZl2gg7SBQIpGe9pTD8kN+yf99yiAd+9aXXtx7GvQ=",
        "ewogICJ0aW1lc3RhbXAiIDogMTY2MTgxMTc2MDkwOSwKICAicHJvZmlsZUlkIiA6ICI0YTUxMjFiYTJjYmI0ZGNkYWY2ZTcyMmY0MDEzMGRkMiIsCiAgInBy" +
            "b2ZpbGVOYW1lIiA6ICJUb21hc2tvX2V1IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewog" +
            "ICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzY3YzllZTYwZjIxYzZmYjc1MWY3MDZjNDIyOGU2MWE4NGU1MjM0" +
            "MzBlN2ZjNTY1YTRlNjQyYmVjMjA5NDc3M2MiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQog" +
            "IH0KfQ=="
    );
    private static final SkinProfile TOWN_COURIER_SKIN = new SkinProfile(
        "SMPTownCourier",
        "deRIFINsv2mLHOwzX2UBj6Yu6ENbJmeetyukwqzz3abab8GO9T8cBufUA6HUHx96jiHBG1Vpo3ynri4DqprNSUuxlmhv+TuXjGEeuCxkV41LYOgR1WR8D5CFkfpKM4k91hARe/U9lpSZ1jJFvcfZAQquXVpx+679C0/8FU+svK48++sVIE5ltAI6QZ9yd68Z9j4SbRqb6uhH59mZfgfpEClSeRt1R1TumiDAuGFJQ/y8ndTHiQ2rdroNqvcGRmhIgeHntOgDwYI4rIAy84PqbNpl/XGpi9aWOq7LEuUjbJDUfQuWO/YPE10wZdMLDtgnMbJ+AnouwnrNvXqhETRY4l+pYm+NSCXUYJ4SuoIOZ3lb2nckjh6kHdD4wvKmxC0v4tfzdPTcesZZNxSYIXwPGJFfjMB6wnHyjiWtiRG3XpFnMbWthxcguxNai6Bv6BKMI8VnPQ1P1Iydi20Q1h+xV2BJEGRwFOYpqtWaYpqkStZzTiBD2Qu+9zo3jHMqfFa4bv5pboR8ziU3m7u5auhHp54njBto3KuOjB0O2uPcGnJnvP9C1OFI1bVIokq0GpiUwlvlMgxCpPBad0QDk5pMC5r9pmE5kvRbwfHx9nTxJOLY8syr49yGDPl2PPb5xvFInGil6IlE3fVwPmwZ159bk1Fb1mzdv+CEwHCdUKzKW0E=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc4MDE3NjI4Nzg5OCwKICAicHJvZmlsZUlkIiA6ICIxZjk0OTQzN2RlYmQ0ODgyYTlhYzZhZmZmN2RhNDcxMSIsCiAgInByb2ZpbGVOYW1lIiA6ICJNaWlra2FLIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2ZkMTM1MDBmMGNkMDJkYjMzMDNiOGRiOTExZTk5NmJmOGZmYWVjNDU1NTU3OGM3OWMwMTMyNDk1ZWEwNGIyN2EiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="
    );
    private static final SkinProfile TOWN_DOCKHAND_SKIN = new SkinProfile(
        "SMPTownDock",
        "VW9Q9wIR8Czgs27Mk5OPDjiCuV3mgnYOy+b5eTg54vXFJRVaLTvXhuaTuc08u50HLQA329JdWyU+ZhB8qLEbCBhIuMy30xoTmMgBQSNx8RokfXE6z6CeA7GRo4WmG0HDTJBfeJUBxyPoWWIpAvo4UnHi8aiRHGUaea5BbC/4Moqh0Lw6Rjhmm5Gi+8VNynkaLLkrwXYpnxIZreIhtrCJLTwFWA0QrZcC8NcKSbVqob3PAAR2sT7c7MP3PauQwRys3Nh/AITGd87E1inDoDpfe+0aasZPuTVRGffwK2vXdsnRjjXat1eCjAeuNS7sJFWzbytuqQfA+C6T+b8otE27ancTcK3vKgYUJ1UpCquzzqKPKStrye+THog6dpGMYwb+SPW2c6KqYtTdaKr61Qq1Nn9VmmfpAuem4GzxMlAX4tVUEqVyXog6OkqVtqA8O29Xhn90qN/k/hFX7uEihgiMj2DEmxwMmybiVk7TeEVcyzgsY3RcD/eJWCSFK6clX0XuOKqFRdp5TA2S/caF0tTdjUa98+uxXzASD15RIei8A+6fnKHku1z5T0xZi/QFpVQyJh5DLuNL4q7rTH3TARBepr3WP4ZwCmXj7oRFFMSkJqF6KxQTeI2qumKYvAcY+4jWrKPf9r1RYmFq2FBvoR3Y+MfkdTasQC4VYFmoBwfGlOk=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc3NzA4OTExOTQ2NSwKICAicHJvZmlsZUlkIiA6ICJiODU0NWMxMDlhZjE0ZGRjYmY4ZjhmZjg4ZTU2NzI4OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJQdGFrb3B5c2tDWiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83YjliM2MyMDRhOGQ1ZWQ5MTdhZmMzZmI4YjYwNjIzNTQ3N2YxMTY5N2IxNTUzMzc2ZmUwMDVkODg5ZGY1MWQ2IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="
    );
    private static final SkinProfile TOWN_SEAMSTRESS_SKIN = new SkinProfile(
        "SMPTownSeam",
        "fhx3u5Izhyo7XLSwmdcVtW4Ln2dMhkjq4LEFBk4+97IbXA3nPBkre1CUnsgI9lDZMXWFQksrfMWfI6ElpVc1bvIqHyDM/3g+5bs3sVx18PEzE3DymtWHfQ/g" +
            "1Ld9VhgPrcaOB1FEJ7H7dgZTFfc4oGRMwYAOnoJfAUiUZG/IXjJpbyiIJP+p8jCaSFf8Hv8iMoZdNjfqrEsadmk2lE+3UrwdgZ7DUmHNXgtPkf7k0w6RxaGV" +
            "PU+05H6WQY9QicmxZzZMXHl2KSyArqyh00l/JcsXXlOtEGRdrAzPvQ2AH3I+0C7DDl4i5mP2t84rBd6eJEIz0/D4A1R+9hc1g0EOoOty/mi3tw71VPHieQdP" +
            "ALefG3A//DtabAkudFltO9WmFSNdtyv9Xqns+UK/OwYXCE+CYq5aDKLPcET060NaQdOSUwqz/dmflzTMQyxp7AtzYZK/aEHdgCGYuYyoPYvZ50XQ+msnG06z" +
            "+Z6/v2KDW4uFKVRhqEJVKyhQXHqyJR5dO3q5Cdtl73NUUeY9k/Qi6Vv4fmfoSzOKyRByO7t6r4jof4V6pZ2Qpm6Ni5SAMUkQSlErHFzTv++H8Ax4u+XiYMl4" +
            "veE1c+oPzAVs9m21+nd96N6Lc/NUdSKzgLC1Upe7rgYvBbK7koOkyYEutCUsEsJnMfWxMmsL5tw1P11bQ2g=",
        "ewogICJ0aW1lc3RhbXAiIDogMTczMzc4MDY4MzY0NSwKICAicHJvZmlsZUlkIiA6ICI1NjExYmE1MjFmNDM0OWYwOWE0MTI5ZTM0NjQzMDM1OSIsCiAgInBy" +
            "b2ZpbGVOYW1lIiA6ICJHdXlOYW1lZEthaW5lIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDog" +
            "ewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2I5MGJkNDI2NDdiZDA0NDE2MzI2YTcyYTQwM2I3MjhmNWQ2" +
            "M2ZhMzhhZThmOGJmZGU4NDllZmU1YTcxM2QxNzAiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAg" +
            "fQogIH0KfQ=="
    );
    private static final SkinProfile TAVERN_HOST_SKIN = new SkinProfile(
        "SMPTavernHost",
        "hmQY02GEy3Nvg4SxW20It+T1B1zyb0amV6PYXNwhpG78XDNY4jRC/1ZHxh8HgfywhXPIo/3MVIEyT7gKAwTfkyRfzq1TU/aB7U8UfBwFKrxTBTsu8ke+ShiXT0RR8THjbhCtP7uqeIsuhdpy1Vz1Bd9P4DUB5/nzNEALzc5c1AGDyZMLOXhIFN5oJgNwn4mBPh8MbUvjtYpzT/T+a/143ndBc4AeZCMAHATGitJLA+MrgbSh1LjgnAc/veeBaawg5rxA/pChI22szKN4Xydfw0KrYx+rFosXzMAIOCHPcFKntPZBoCl1hBtEflxpMfXVpfCd68D6Ce4UwAS9vCbrtA2+PVgSgfrYpBYVyRJUwlElz04+zwpeHPvSUzq7ICkSFodtu7J05ztF+Wa6vWtxS0sxIsVoQid0Ji1c156jK6B8lwlPvHD0L5acbbjQMnsWIjgqgdrRkuKMH4P7j7KwXFPJhMCpmayjnC+1eULgVwAuINE66mihrLLKiXx1S1WrqQENzCitZON54rXhQf0hIVboMyOwjPJPH/BXhWZxSDzFcA3cx9D9EXtQFF48DkLh9XNE47W5XcPccDXDCTzLIelwtSZgc+dQV1SDRhIsEI7iJoCypEymKIQakgkNNdEd6vDtIvJw8hakq3kPiOo99bTjc5Dd7B222n9NWIA5Sko=",
        "ewogICJ0aW1lc3RhbXAiIDogMTYzNDQ1MzE2NzA4MSwKICAicHJvZmlsZUlkIiA6ICIxZjEyNTNhYTVkYTQ0ZjU5YWU1YWI1NmFhZjRlNTYxNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJOb3RNaUt5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzJlMjViN2E5MWE0OWI1MWZlNjViMDQzNTU0MWJkMTI0YjBiNDE0ZmQxM2QzMTc1YmIxZDY5MWU4NDA4M2E1MjgiCiAgICB9CiAgfQp9"
    );
    private static final SkinProfile TAVERN_REGULAR_SKIN = new SkinProfile(
        "SMPTavernRegular",
        "TPKHJ8ueaqgM8faVTvBHR6k1CdwjaeOfONblJPM9knwbFdQpWcaPoGKDWUmTTSKZSAss7DvfUlawXduCh+KcMdO5o9PeHczs12Kre0q/bhC5JpNsd1H8W/Pqzj8blkwBwFD/orHYqByCL3aCPVb+olnQNZCMhYi+Tbd4c4i30ZuCR7T0q8D8y/VTEMoaUJavH1DeujOras9mAIl272v0ayXy1TqiV1fBZxzuR6e9rDujTVqcfmXSyBk17EnVCpsgSTSaYRDc0yW0dLSyhSu+A8HrwoJ8FbtjdfjrDIT0dwEZABAGhoS2T5bDN08WgZiFrPw1/CYwQtXINketJGlMqkN4yLZyyZuz1BePig1ccZvnTazp13oGvotGO8oY1WiRcSZDVRKyyGtJJX/FDYEVXLLGmStUiMlbwf3zxDNC6d0I1ymly+gn2iZjmp66IAB2hgfxSuAYu2BLQXHDZHXfcqm6TvQlf1HJYfnNo3FUF1wLBcMdUYEvwd2hCDmwGSR9sMap/clNP/S++Im/HYUinRL5n32Y8IFkMAlPZQYAs01S8YJsyNhECr7BsEBQsZck3A7w+I0NQ9OJTZvgBrRQW6rXROSSIpsXv7uA2aSmfCpGo9xW9MOPXSdtoDnQBKR8O19eoTPlDIBjkvgGQvGQnP9mSL7/Ao2KiZ585jUhV4o=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc4NDA0NDExNDQ2MCwKICAicHJvZmlsZUlkIiA6ICI2NWI5NTU1YWQyMGU0NWM5YjFkNmU3MjQwNjU0NTBkNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJNSEZfTWVybCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8xNTFiOTdlNjZkNzQ0NGRkMDFjMWFiZDljMTQ4ZGQ2YTRhYWRmZDc3NTczNWYzY2YyMjA4OWFlYjNjYjljNjg3IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="
    );
    private static final SkinProfile TAVERN_TIPSY_SKIN = new SkinProfile(
        "SMPTavernTipsy",
        "yUzIdkNGIhPOMow5CWTDFSHYWz7/vMO70PMhd+jHFbavcoo9La/MURUXgOjatXlhz9lLv7hwY6nRlDXUDULB+syyMKkvRHeEhA9l31IlYXI8SOeknBkOaaLFfHll20u9xLvyacmUIn2+X92XFQoIKYUaX1yN8cIfjlwA8NoFYE9z6EM1R8hS3h5lhexsjiK8vsDjYOBEz5AJFlW3eH+q/y+OAPkACwbkj71rvWePsDEOOWdELPwMaBpGcmS1Drc0PtQbuYICQ1xfIwof2q3A+5NGZx6se5naj2SWnIQx49kt93QQG/5ef/su+dxw1PMQeo+8Wyi9zRaiOtA696PDFP8yyvMnTRbSolxT62eVQzOlwBhCyah6xCaBG4VJxqOBovIlsO062ElIui5Ib/zLeExUIq57uxP2mflz+STXn99hqJw47HlSWq4j6T40tk321Eow2SgCs8NSllw5L99qWqe5A3vTcIFGchLTdsmlQGAfttZ1gvx+SmzFWCUKFCffduU4y2b+/XnirJ9v2nQNmoKtitE3SqTOe8bD22TyUhN3N+pFywQNAa42Z0S06P39PzUpXoNdEC89ts5hMIC4moEl4XiVpSi/nFvN+fLCFh5rdRefUWO7x8eQaFMPViQUg04YpLCYKtGFpaqcBRkOGXKWUKapOxVmglGi1jv/gXM=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc0NDgwNDI0NDA2NywKICAicHJvZmlsZUlkIiA6ICIzMzU3MWJiY2UyMDE0MTRiYmNkMDYyMjEyZTI4MjBlMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGFkb21JbmF0b3I0NzgiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2Y3YWE5OTMxYjJkMTVmOWNhOTgzZTQ1NTEzYTYzNTI5OTE3NWJhNmYwZDc5NDdhMDJiYWI4MGJmNDJiOWQxIgogICAgfQogIH0KfQ=="
    );
    private static final SkinProfile SABLE_SKIN = new SkinProfile(
        "SMPSableBroker",
        "hGZK+ZPpJusYMUDiBbUTiDyFbphuv/2euVNz2LGPS5CfRMM8+Vhu6H3upGO3DU8QA8gmSLti45WO+pA2KO65H/plMRfS5WNPSon1+5ZyEHVgE5qg/kL8ZR0oCxKRo0ooyQOL6KESA/ZZaatCd0T2W66vneXkXcpgTb0fknlPMmNuG2U7fxh2tAHmmOgkpvSfBHnoIs1PZk5xIAy58n1yhiq80P+1YHVREIg6w5lCyh6YSHMycUZ36SDLwSRAdwcf6tlkN7CdSOLUj3J6vKK9mn4LrteDVKta8g/gkgO9v/PkNlh+5hblEp151S04+zb9aUHmMPX+WJdIHXHt8KcKxfOV5M+Q6PWj+QW/V7j6RGs35Mj8xSqbAEPN6Lo10ZBS02HTFzueU4cehs2GIqVNKt04YRmmya/rs3AvP5aUXeg10ItoA+mum/F4huT7hT9hFHrnUN0PBsgJzPfcMWyrvklQ6UtlFII0+jLD+9dQTD8VEToSCakIVg6PJdrFzoiCefd7VByhlm56TOxLINYbKW7F4FPOFP7rxalE+vNlnH2JBa01E0meOCvcDl797FfcM6A7QQCMq9Z4iOoC3W+0vISVbm+6XKn8IQ1NHg9NBZpqkCRWO1hNpOr1pvbvqDJkd98e6cqCRfhNvVewR0HMslRmFoMy2pZD6RnNLIezytA=",
        "ewogICJ0aW1lc3RhbXAiIDogMTc0Nzc5MTk2MjY0NSwKICAicHJvZmlsZUlkIiA6ICIxMjE4YWNiNDJiYzA0MzY4YjIxOTU4ZTZiYWU2NDMyMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJQYXRhdGplTUMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjhlNGE4MjUzMGE2Y2MxZDY4ZTJhM2EzYjk5MTVjYzc0MGU1NGI2M2U3OTI1MGJlNzIwZDFhYTA1MzExZGNlNyIKICAgIH0KICB9Cn0="
    );

    private final SMPCore plugin;
    private final GuideNpcManager guideNpcManager;
    private final NamespacedKey npcTypeKey;
    private final NamespacedKey dealerHitboxOwnerKey;

    public CitizensGuideBridge(SMPCore plugin, GuideNpcManager guideNpcManager, NamespacedKey npcTypeKey) {
        this.plugin = plugin;
        this.guideNpcManager = guideNpcManager;
        this.npcTypeKey = npcTypeKey;
        this.dealerHitboxOwnerKey = new NamespacedKey(plugin, "dealer_hitbox_owner");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refreshLoadedNpcs();
    }

    @Override
    public Entity spawn(GuideNpcType type, Location location) {
        if (!isReady() || type == null || location == null || location.getWorld() == null) {
            return null;
        }

        NPC npc = registry().createNPC(type.entityType(), type.displayName());
        configure(npc, type);
        boolean spawned = npc.spawn(location, SpawnReason.CREATE, entity -> tagSpawnedEntity(npc, type, entity));
        if (!spawned) {
            npc.destroy();
            return null;
        }

        registry().saveToStore();
        return npc.getEntity();
    }

    @Override
    public int removeNearest(GuideNpcType type, Location origin, double radius) {
        if (!isReady() || type == null || origin == null || origin.getWorld() == null) {
            return 0;
        }
        NPC nearest = guideNpcs(type).stream()
            .filter(npc -> npcLocation(npc) != null)
            .filter(npc -> npcLocation(npc).getWorld().equals(origin.getWorld()))
            .filter(npc -> npcLocation(npc).distanceSquared(origin) <= radius * radius)
            .min(Comparator.comparingDouble(npc -> npcLocation(npc).distanceSquared(origin)))
            .orElse(null);
        if (nearest == null) {
            return 0;
        }

        removeDealerHitboxes(nearest.getId());
        nearest.destroy();
        registry().saveToStore();
        return 1;
    }

    @Override
    public List<Location> locations(GuideNpcType type) {
        if (!isReady() || type == null) {
            return List.of();
        }
        List<Location> locations = new ArrayList<>();
        for (NPC npc : guideNpcs(type)) {
            Location location = npcLocation(npc);
            if (location != null) {
                locations.add(location);
            }
        }
        return locations;
    }

    @Override
    public int refreshLoadedNpcs() {
        if (!isReady()) {
            return 0;
        }
        int refreshed = 0;
        for (NPC npc : allGuideNpcs()) {
            GuideNpcType type = typeOf(npc);
            if (type == null) {
                continue;
            }
            Location location = npcLocation(npc);
            boolean wasSpawned = npc.isSpawned();
            if (wasSpawned) {
                npc.despawn(DespawnReason.PLUGIN);
            }

            configure(npc, type);

            if (wasSpawned && location != null) {
                npc.spawn(location, SpawnReason.RESPAWN, entity -> tagSpawnedEntity(npc, type, entity));
            } else if (npc.isSpawned()) {
                tagSpawnedEntity(npc, type, npc.getEntity());
            }
            refreshed++;
        }
        if (refreshed > 0) {
            registry().saveToStore();
        }
        return refreshed;
    }

    @Override
    public void shutdown() {
        removeAllDealerHitboxes();
        if (isReady()) {
            registry().saveToStore();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcRightClick(NPCRightClickEvent event) {
        GuideNpcType type = typeOf(event.getNPC());
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        event.setDelayedCancellation(false);
        Player clicker = event.getClicker();
        Entity interactionTarget = event.getNPC().getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> guideNpcManager.openMenuFromNpc(clicker, type, interactionTarget));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpcSpawn(NPCSpawnEvent event) {
        GuideNpcType type = typeOf(event.getNPC());
        if (type != null) {
            Bukkit.getScheduler().runTask(plugin, () -> tagSpawnedEntity(event.getNPC(), type, event.getNPC().getEntity()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpcDespawn(NPCDespawnEvent event) {
        GuideNpcType type = typeOf(event.getNPC());
        if (type == null) {
            return;
        }
        removeDealerHitboxes(event.getNPC().getId());
        if (plugin.getNpcHologramManager() != null) {
            plugin.getNpcHologramManager().hide(hologramOwner(type, event.getNPC()));
        }
    }

    private void configure(NPC npc, GuideNpcType type) {
        npc.setName(type.displayName());
        npc.setBukkitEntityType(type.entityType());
        npc.setProtected(true);
        npc.setUseMinecraftAI(false);
        npc.data().setPersistent(NPC_DATA_KEY, true);
        npc.data().setPersistent(NPC_TYPE_DATA_KEY, type.id());
        npc.data().setPersistent(NPC.Metadata.COLLIDABLE, false);
        npc.data().setPersistent(NPC.Metadata.DEFAULT_PROTECTED, true);
        npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
        npc.data().setPersistent(NPC.Metadata.PICKUP_ITEMS, false);
        npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST, true);
        npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, true);
        npc.data().setPersistent(NPC.Metadata.SILENT, true);
        npc.data().setPersistent(NPC.Metadata.TRACKING_RANGE, 48);
        npc.data().setPersistent(NPC.Metadata.USE_MINECRAFT_AI, false);

        if (type.entityType() == EntityType.PLAYER) {
            SkinProfile skinProfile = skinProfile(type);
            SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
            skin.clearTexture();
            skin.setSkinPersistent(skinProfile.name(), skinProfile.signature(), skinProfile.value());
            skin.setFetchDefaultSkin(false);
            skin.setShouldUpdateSkins(false);

            Equipment equipment = npc.getOrAddTrait(Equipment.class);
            equipment.set(Equipment.EquipmentSlot.HAND, new ItemStack(type.handItem()));
            equipment.set(Equipment.EquipmentSlot.OFF_HAND, new ItemStack(offHandItem(type)));
        }
        if (type != GuideNpcType.SPAWN_GUIDE) {
            configureCloseLook(npc);
        }

        HologramTrait hologram = npc.getOrAddTrait(HologramTrait.class);
        hologram.clear();
    }

    private void configureCloseLook(NPC npc) {
        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        lookClose.lookClose(true);
        lookClose.setRange(CLOSE_LOOK_RANGE);
        lookClose.setRandomLook(false);
        lookClose.setRandomlySwitchTargets(false);
        lookClose.setTargetNPCs(false);
        lookClose.setLinkedBody(true);
        lookClose.setHeadOnly(false);
        lookClose.setRealisticLooking(true);
        lookClose.setDisableWhileNavigating(true);
    }

    private void tagSpawnedEntity(NPC npc, GuideNpcType type, Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().set(npcTypeKey, PersistentDataType.STRING, type.id());
        entity.addScoreboardTag("smpcore_npc");
        entity.addScoreboardTag(type.scoreboardTag());
        entity.setInvulnerable(true);
        entity.setSilent(true);
        if (entity instanceof LivingEntity living) {
            living.setCanPickupItems(false);
            living.setCollidable(false);
            living.setRemoveWhenFarAway(false);
            AttributeInstance scale = living.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(npcScale(type));
            }
        }
        if (type == GuideNpcType.FETCH_HOUND && entity instanceof Wolf wolf) {
            wolf.setAngry(false);
            wolf.setInterested(true);
            wolf.setSitting(false);
            wolf.setCollarColor(DyeColor.YELLOW);
        } else if (type == GuideNpcType.TOWN_FOX && entity instanceof Fox fox) {
            fox.setFoxType(Fox.Type.RED);
            fox.setSitting(false);
            fox.setSleeping(false);
        } else if (type == GuideNpcType.TOWN_PARROT && entity instanceof Parrot parrot) {
            parrot.setVariant(Parrot.Variant.BLUE);
            parrot.setSitting(false);
        }
        if (plugin.getNpcHologramManager() != null) {
            plugin.getNpcHologramManager().show(entity, hologramOwner(type, npc), type.nameplate(), type.hologramOffset());
        }
        if (type == GuideNpcType.DEALER) {
            ensureDealerHitbox(npc, entity);
        }
    }

    private void ensureDealerHitbox(NPC npc, Entity dealer) {
        List<Interaction> hitboxes = dealerHitboxes(npc.getId());
        Interaction hitbox = hitboxes.isEmpty()
            ? dealer.getWorld().spawn(dealer.getLocation(), Interaction.class)
            : hitboxes.getFirst();
        for (int i = 1; i < hitboxes.size(); i++) {
            hitboxes.get(i).remove();
        }

        if (!hitbox.getWorld().equals(dealer.getWorld())
            || hitbox.getLocation().distanceSquared(dealer.getLocation()) > 0.000001D) {
            hitbox.teleport(dealer.getLocation());
        }
        hitbox.setInteractionWidth(DEALER_HITBOX_WIDTH);
        hitbox.setInteractionHeight(DEALER_HITBOX_HEIGHT);
        hitbox.setResponsive(true);
        hitbox.setGravity(false);
        hitbox.setInvulnerable(true);
        hitbox.setSilent(true);
        hitbox.setPersistent(false);
        hitbox.addScoreboardTag("smpcore_npc");
        hitbox.addScoreboardTag(DEALER_HITBOX_TAG);
        hitbox.addScoreboardTag(GuideNpcType.DEALER.scoreboardTag());
        hitbox.getPersistentDataContainer().set(npcTypeKey, PersistentDataType.STRING, GuideNpcType.DEALER.id());
        hitbox.getPersistentDataContainer().set(dealerHitboxOwnerKey, PersistentDataType.INTEGER, npc.getId());
    }

    private List<Interaction> dealerHitboxes(int npcId) {
        List<Interaction> hitboxes = new ArrayList<>();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Interaction interaction : world.getEntitiesByClass(Interaction.class)) {
                Integer ownerId = interaction.getPersistentDataContainer()
                    .get(dealerHitboxOwnerKey, PersistentDataType.INTEGER);
                if (ownerId != null && ownerId == npcId) {
                    hitboxes.add(interaction);
                }
            }
        }
        return hitboxes;
    }

    private void removeDealerHitboxes(int npcId) {
        dealerHitboxes(npcId).forEach(Entity::remove);
    }

    private void removeAllDealerHitboxes() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Interaction interaction : world.getEntitiesByClass(Interaction.class)) {
                if (interaction.getScoreboardTags().contains(DEALER_HITBOX_TAG)) {
                    interaction.remove();
                }
            }
        }
    }

    private SkinProfile skinProfile(GuideNpcType type) {
        return switch (type) {
            case SPAWN_GUIDE -> MIRA_SKIN;
            case CORRUPTION_WARDEN -> VEYR_SKIN;
            case DUNGEON_KEEPER -> DUNGEON_MASTER_SKIN;
            case MAYOR -> MAYOR_SKIN;
            case GEAR_EXPERT -> ORIN_SKIN;
            case BREWMASTER -> BRAM_SKIN;
            case CARDSHARP -> ROOK_SKIN;
            case DEALER -> DEALER_SKIN;
            case ROULETTE_CROUPIER -> ROULETTE_CROUPIER_SKIN;
            case DUELMASTER -> DUELMASTER_SKIN;
            case GOBLIN_HUNTER -> GOBLIN_HUNTER_SKIN;
            case MINER -> MINER_SKIN;
            case FARMER -> FARMER_SKIN;
            case WITCH -> OVERSEER_SKIN;
            case OVERSEER -> OVERSEER_SKIN;
            case BEASTWARDEN -> BEASTWARDEN_SKIN;
            case BOSSBROKER -> BOSSBROKER_SKIN;
            case BLACK_MARKETEER -> SABLE_SKIN;
            case FISHER -> FISHER_SKIN;
            case TOWN_BAKER -> TOWN_BAKER_SKIN;
            case TOWN_MASON -> TOWN_MASON_SKIN;
            case TOWN_COURIER -> TOWN_COURIER_SKIN;
            case TOWN_DOCKHAND -> TOWN_DOCKHAND_SKIN;
            case TOWN_SEAMSTRESS -> TOWN_SEAMSTRESS_SKIN;
            case TAVERN_HOST -> TAVERN_HOST_SKIN;
            case TAVERN_REGULAR -> TAVERN_REGULAR_SKIN;
            case TAVERN_TIPSY -> TAVERN_TIPSY_SKIN;
            case FETCH_HOUND, TOWN_CAT, TOWN_FOX, TOWN_PARROT, HIDDEN_ILLUSIONER ->
                throw new IllegalArgumentException(type.displayName() + " is not a player NPC.");
        };
    }

    private double npcScale(GuideNpcType type) {
        return switch (type) {
            case SPAWN_GUIDE -> 0.94D;
            case MAYOR -> 1.0D;
            case GEAR_EXPERT -> 0.98D;
            case FETCH_HOUND -> 1.0D;
            case TOWN_CAT -> 0.92D;
            case TOWN_FOX, TOWN_PARROT -> 0.94D;
            case HIDDEN_ILLUSIONER, TOWN_BAKER, TOWN_MASON, TOWN_COURIER, TOWN_DOCKHAND, TOWN_SEAMSTRESS,
                 TAVERN_HOST, TAVERN_REGULAR, TAVERN_TIPSY -> 1.0D;
            default -> 1.04D;
        };
    }

    private Material offHandItem(GuideNpcType type) {
        return switch (type) {
            case SPAWN_GUIDE, MAYOR -> type.icon();
            case CORRUPTION_WARDEN -> Material.SCULK_SHRIEKER;
            case GEAR_EXPERT -> Material.SMITHING_TABLE;
            case DUNGEON_KEEPER -> Material.RESPAWN_ANCHOR;
            case BREWMASTER -> Material.BREWING_STAND;
            case CARDSHARP -> Material.SUNFLOWER;
            case DEALER -> Material.GOLD_NUGGET;
            case ROULETTE_CROUPIER -> Material.RECOVERY_COMPASS;
            case DUELMASTER -> Material.SHIELD;
            case GOBLIN_HUNTER -> Material.PLAYER_HEAD;
            case MINER -> Material.LANTERN;
            case FARMER -> Material.WHEAT;
            case WITCH -> Material.POTION;
            case OVERSEER -> Material.WRITABLE_BOOK;
            case BEASTWARDEN -> Material.DIAMOND_HORSE_ARMOR;
            case BOSSBROKER -> Material.GOLD_INGOT;
            case BLACK_MARKETEER -> Material.GOLD_NUGGET;
            case FISHER -> Material.COD;
            case TOWN_BAKER -> Material.WHEAT;
            case TOWN_MASON -> Material.STONECUTTER;
            case TOWN_COURIER -> Material.PAPER;
            case TOWN_DOCKHAND -> Material.OAK_BOAT;
            case TOWN_SEAMSTRESS -> Material.WHITE_WOOL;
            case TAVERN_HOST -> Material.COMPASS;
            case TAVERN_REGULAR -> Material.GOLD_NUGGET;
            case TAVERN_TIPSY -> Material.BOWL;
            case FETCH_HOUND, TOWN_CAT, TOWN_FOX, TOWN_PARROT, HIDDEN_ILLUSIONER -> Material.AIR;
        };
    }

    private String hologramOwner(GuideNpcType type, NPC npc) {
        return type.id() + ":citizens:" + npc.getId();
    }

    private boolean isReady() {
        return Bukkit.getPluginManager().isPluginEnabled("Citizens") && CitizensAPI.hasImplementation();
    }

    private NPCRegistry registry() {
        return CitizensAPI.getNPCRegistry();
    }

    private List<NPC> allGuideNpcs() {
        List<NPC> npcs = new ArrayList<>();
        for (NPC npc : registry()) {
            if (typeOf(npc) != null) {
                npcs.add(npc);
            }
        }
        return npcs;
    }

    private List<NPC> guideNpcs(GuideNpcType type) {
        List<NPC> npcs = new ArrayList<>();
        for (NPC npc : registry()) {
            if (typeOf(npc) == type) {
                npcs.add(npc);
            }
        }
        return npcs;
    }

    private GuideNpcType typeOf(NPC npc) {
        if (npc == null) {
            return null;
        }
        Object marker = npc.data().get(NPC_TYPE_DATA_KEY);
        if (marker instanceof String value) {
            GuideNpcType type = GuideNpcType.byId(value);
            if (type != null) {
                return type;
            }
        }
        for (GuideNpcType type : GuideNpcType.values()) {
            if (type.displayName().equals(npc.getName()) || type.displayName().equals(npc.getRawName())) {
                return type;
            }
        }
        return null;
    }

    private Location npcLocation(NPC npc) {
        if (npc == null) {
            return null;
        }
        Entity entity = npc.getEntity();
        if (entity != null && entity.isValid()) {
            return entity.getLocation();
        }
        return npc.getStoredLocation();
    }

    private record SkinProfile(String name, String signature, String value) {
    }
}
