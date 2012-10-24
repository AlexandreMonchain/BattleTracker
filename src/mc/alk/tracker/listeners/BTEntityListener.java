package mc.alk.tracker.listeners;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import mc.alk.tracker.Defaults;
import mc.alk.tracker.Tracker;
import mc.alk.tracker.TrackerInterface;
import mc.alk.tracker.controllers.ConfigController;
import mc.alk.tracker.controllers.MessageController;
import mc.alk.tracker.controllers.TrackerController;
import mc.alk.tracker.objects.SpecialType;
import mc.alk.tracker.objects.Stat;
import mc.alk.tracker.objects.WLT;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;


public class BTEntityListener implements Listener{
	//	private static final int DAMAGE_TIMEOUT = 60000;
	static JavaPlugin plugin;

	ConcurrentHashMap<String,Long> lastDamageTime = new ConcurrentHashMap<String,Long>();
	ConcurrentHashMap<String,RampageStreak> lastKillTime = new ConcurrentHashMap<String,RampageStreak>();
	Random r = new Random();
	TrackerInterface playerTi;
	TrackerInterface worldTi;
	int count = 0;

	class RampageStreak{
		Long time; int nkills;
		public RampageStreak(Long t, int nk){this.time = t; this.nkills = nk;}
	}

	public BTEntityListener(){
		playerTi = Tracker.getInterface(Defaults.PVP_INTERFACE);
		worldTi = Tracker.getInterface(Defaults.PVE_INTERFACE);
		plugin = Tracker.getSelf();
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onEntityDeath(EntityDeathEvent event) {
		String target, killer;
		boolean targetPlayer = false, killerPlayer = false;
		boolean isMelee = true;
		ItemStack killingWeapon = null;

		/// Get our hapless target
		Entity targetEntity = event.getEntity();
		if (targetEntity instanceof Player){
			target = ((Player)targetEntity).getName();
			targetPlayer = true;
		} else if (targetEntity instanceof Tameable){
			target = "Tamed" + targetEntity.getType().getName();
		} else {
			target = targetEntity.getType().getName();
		}
		//		FileLogger.log("onEntityDeath " + target +" targetPlayer=" + targetPlayer +"   tracking=" + TrackerController.dontTrack(target));
		/// Should we be tracking this person
		if (targetPlayer && TrackerController.dontTrack(target)){
			if (event instanceof PlayerDeathEvent)
				((PlayerDeathEvent) event).setDeathMessage(""); /// Set to none, will cancel all non pvp messages
			return;
		}

		EntityDamageEvent lastDamageCause = event.getEntity().getLastDamageCause();

		/// Get our killer
		if (lastDamageCause instanceof EntityDamageByEntityEvent){
			EntityDamageByEntityEvent edbee = (EntityDamageByEntityEvent) lastDamageCause;
			if (edbee.getDamager() instanceof Player) { /// killer is player
				Player pk = ((Player) edbee.getDamager());
				killer = pk.getName();
				killerPlayer = true;
				killingWeapon = pk.getItemInHand();
			} else if (edbee.getDamager() instanceof Projectile) { /// we have some sort of projectile
				isMelee = false;
				Projectile proj = (Projectile) edbee.getDamager();
				if (proj.getShooter() instanceof Player){ /// projectile was shot by a player
					killerPlayer = true;
					killer = ((Player) proj.getShooter()).getName();
				} else if (proj.getShooter() != null){ /// projectile shot by some mob, or other source
					killer = proj.getShooter().getType().getName();
				} else {
					killer = "unknown"; /// projectile was null?
				}
			} else { /// Killer is not a player
				killer = edbee.getDamager().getType().getName();
			}
		} else {
			if (lastDamageCause == null || lastDamageCause.getCause() == null)
				killer  = "unknown";
			else
				killer = lastDamageCause.getCause().name();
		}
		//		FileLogger.log("onEntityDeath " + target +" targetPlayer=" + targetPlayer +"   tracking=" + TrackerController.dontTrack(target)+
		//				"  killerPlayer=" + killerPlayer +"   killer=" + killer);

		if (killerPlayer && TrackerController.dontTrack(killer))
			return;
		/// Decide what to do
		if (targetPlayer && killerPlayer){
			/// Check to see if we add the records
			if (ConfigController.getBoolean("trackPvP",true))
				addRecord(playerTi,killer,target,WLT.WIN,true);
			/// Check to see if an admin has disabled death messages
			PlayerDeathEvent pde = (PlayerDeathEvent) event;
			if (Defaults.DISABLE_PVP_MESSAGES){
				pde.setDeathMessage(null);
				return;
			}
			/// Check sending messages
			if (ConfigController.getBoolean("sendPVPDeathMessages",true)){
				final String wpn = killingWeapon != null ? killingWeapon.getType().name().toLowerCase() : null;

				String msg = getPvPDeathMessage(killer,target,isMelee,playerTi,wpn);
				pde.setDeathMessage(msg);
			} else if (!ConfigController.getBoolean("showBukkitPVPMessages",false)){
				pde.setDeathMessage(null);
			}
		} else if (!targetPlayer && !killerPlayer){ /// mobs killing each other, or dying by traps
			/// Do nothing
		} else { /// One player, One other
			/// Get rid of Craft before mobs.. CraftSpider -> Spider
			if (!killerPlayer && killer.contains("Craft")){
				killer = killer.replaceAll("Craft", "");}
			if (!targetPlayer && target.contains("Craft")){
				target = target.replaceAll("Craft", "");}

			/// Should we track the kills?
			if (ConfigController.getBoolean("trackPvE",true)){
				addRecord(worldTi, killer,target,WLT.WIN, false);}

			/// Check message sending
			if (targetPlayer && event instanceof PlayerDeathEvent){
				/// Check to see if an admin has disabled death messages
				PlayerDeathEvent pde = (PlayerDeathEvent) event;
				if (Defaults.DISABLE_PVE_MESSAGES){
					pde.setDeathMessage(null);
					return;
				}

				if (ConfigController.getBoolean("sendPVEDeathMessages",true)){
					final String wpn = killingWeapon != null ? killingWeapon.getType().name().toLowerCase() : null;
					String msg = getPvEDeathMessage(killer,target,isMelee,worldTi,wpn);
					pde.setDeathMessage(msg);
				} else if (!ConfigController.getBoolean("showBukkitPVEMessages",false)){
					pde.setDeathMessage(null);
				}
			}
		}
	}

	public String getPvPDeathMessage(String killer, String target, boolean isMeleeDeath,
			TrackerInterface ti, String killingWeapon){

		/// Check for a rampage
		try {
			RampageStreak lastKill = lastKillTime.get(killer);
			long now = System.currentTimeMillis() ;
			if (lastKill != null && now - lastKill.time < Defaults.RAMPAGE_TIME){
				lastKill.nkills++;
				lastKill.time = now;
				return MessageController.getSpecialMessage(SpecialType.RAMPAGE, lastKill.nkills, killer,target, killingWeapon);
			} else {
				lastKillTime.put(killer, new RampageStreak(now, 1));
			}
		} catch (Exception e){
			/// in the unlikely case of a Concurrent modification exception, lets not crash
			/// but instead send a normal message
		}

		/// If a normal streak
		/// Player has a multiple of x, send they are on a streak message
		Stat stat = ti.loadPlayerRecord(killer);
		final int streak = stat.getStreak();
		boolean hasStreak = MessageController.contains("special.streak."+streak);
		/// they are on a streak
		if (hasStreak || streak != 0 && Defaults.STREAK_EVERY != 0 && streak % Defaults.STREAK_EVERY== 0){
			return MessageController.getSpecialMessage(SpecialType.STREAK, streak, killer,target, killingWeapon);
		} else { /// Display a normal kill message
			return MessageController.getPvPMessage(isMeleeDeath,killer, target, killingWeapon);
		}
	}

	public String getPvEDeathMessage(String p1, String p2, boolean isMeleeDeath, TrackerInterface ti, String killingWeapon){
		return MessageController.getPvEMessage(isMeleeDeath, p1, p2,killingWeapon);
	}

	public static void addRecord(final TrackerInterface ti,final String e1, final String e2,
			final WLT record, final boolean saveIndividualRecord){
		Bukkit.getScheduler().scheduleAsyncDelayedTask(plugin, new Runnable(){
			@Override
			public void run() {
				ti.addPlayerRecord(e1, e2, record, saveIndividualRecord);
			}
		});
	}
}
