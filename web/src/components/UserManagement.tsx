import React, { useState, useEffect } from 'react';
import { 
  Users, 
  Shield, 
  UserPlus, 
  Trash2, 
  KeyRound, 
  Activity, 
  Globe, 
  Search, 
  TrendingUp, 
  HardDrive, 
  Check, 
  AlertTriangle, 
  RefreshCw, 
  Info,
  UserCheck
} from 'lucide-react';
import { listUsers, saveUser, deleteUser, getCurrentUser } from '../utils/api';

interface UserData {
  username: string;
  role: string;
  permissions: string[];
}

export const UserManagement: React.FC = () => {
  const [users, setUsers] = useState<UserData[]>([]);
  const [currentUser, setCurrentUser] = useState<string | null>(null);
  
  // Form states
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<'ADMIN' | 'USER'>('USER');
  const [permissions, setPermissions] = useState<string[]>(['health']);
  
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  
  const [editingUsername, setEditingUsername] = useState<string | null>(null);

  const fetchUsersData = async (showRefreshIndicator = false) => {
    if (showRefreshIndicator) setRefreshing(true);
    setError(null);
    try {
      const allUsers = await listUsers();
      setUsers(allUsers);
      
      const me = await getCurrentUser();
      setCurrentUser(me.username);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to retrieve user registry');
    } finally {
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchUsersData();
  }, []);

  const handlePermissionToggle = (perm: string) => {
    if (permissions.includes(perm)) {
      setPermissions(permissions.filter(p => p !== perm));
    } else {
      setPermissions([...permissions, perm]);
    }
  };

  const handleResetForm = () => {
    setUsername('');
    setPassword('');
    setRole('USER');
    setPermissions(['health']);
    setEditingUsername(null);
  };

  const handleEditClick = (user: UserData) => {
    setEditingUsername(user.username);
    setUsername(user.username);
    setPassword('');
    setRole(user.role as 'ADMIN' | 'USER');
    setPermissions(user.permissions);
    setError(null);
    setSuccess(null);
  };

  const handleFormSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    
    if (!username.trim()) {
      setError('Username is required');
      return;
    }
    
    if (!editingUsername && !password) {
      setError('Password is required for new users');
      return;
    }

    setLoading(true);
    try {
      await saveUser({
        username: username.trim(),
        password: password || undefined,
        role,
        permissions: role === 'ADMIN' ? ['health', 'analytics', 'inspector', 'longtime', 'pterodactyl'] : permissions
      });
      
      setSuccess(
        editingUsername 
          ? `User "${username}" updated successfully!` 
          : `User "${username}" created successfully!`
      );
      
      handleResetForm();
      await fetchUsersData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Operation failed');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteClick = async (usernameToDelete: string) => {
    if (!window.confirm(`Are you sure you want to delete user "${usernameToDelete}"?`)) {
      return;
    }

    setError(null);
    setSuccess(null);
    
    try {
      await deleteUser(usernameToDelete);
      setSuccess(`User "${usernameToDelete}" deleted successfully!`);
      await fetchUsersData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete user');
    }
  };

  const availableTabs = [
    { key: 'health', name: 'Server Health', desc: 'TPS, MSPT & hardware health dashboards', icon: Activity },
    { key: 'analytics', name: 'Player Analytics', desc: 'Retention statistics & play punchcards', icon: Globe },
    { key: 'inspector', name: 'Player Inspector', desc: 'Minecraft skin renders & session tracking', icon: Search },
    { key: 'longtime', name: 'Longtime Graphs', desc: 'Historical performance charts &resolutions', icon: TrendingUp },
    { key: 'pterodactyl', name: 'Pterodactyl Panel', desc: 'Console controls & backup configurations', icon: HardDrive }
  ];

  return (
    <div className="space-y-6">
      
      {/* Messages */}
      {error && (
        <div className="p-4 rounded-2xl bg-red-500/10 border border-red-500/15 flex items-start gap-3 text-plan-red text-xs animate-shake">
          <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
          <div className="font-semibold">
            <span>Administrative Error</span>
            <p className="mt-0.5 opacity-95 font-medium">{error}</p>
          </div>
        </div>
      )}

      {success && (
        <div className="p-4 rounded-2xl bg-emerald-500/10 border border-emerald-500/15 flex items-center gap-3 text-emerald-500 text-xs">
          <Check className="w-5 h-5 shrink-0 animate-bounce" />
          <div className="font-semibold">
            <span>Action Completed</span>
            <p className="mt-0.5 opacity-95 font-medium">{success}</p>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left Side: Users list */}
        <div className="lg:col-span-2 bg-white dark:bg-plan-card border border-slate-200 dark:border-slate-800/80 rounded-2xl p-6 shadow-sm dark:shadow-xl relative overflow-hidden transition-all">
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-plan-cyan/15 flex items-center justify-center border border-plan-cyan/20">
                <Users className="w-5 h-5 text-plan-cyan" />
              </div>
              <div>
                <h3 className="text-sm font-black tracking-tight text-slate-800 dark:text-white">User Registry</h3>
                <span className="text-[9.5px] opacity-60 font-bold uppercase tracking-wider block mt-0.5">Manage access control and credentials</span>
              </div>
            </div>
            
            <button 
              onClick={() => fetchUsersData(true)} 
              disabled={refreshing}
              className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white transition-all disabled:opacity-50"
              title="Refresh User List"
            >
              <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-slate-200/60 dark:border-slate-800/40 text-[9px] uppercase tracking-wider font-black text-slate-400 dark:text-slate-500">
                  <th className="py-3 px-4">Username</th>
                  <th className="py-3 px-4">Role</th>
                  <th className="py-3 px-4">Tab View Permissions</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800/20">
                {users.map((u) => {
                  const isSelf = u.username === currentUser;
                  const isAdminRole = u.role === 'ADMIN';

                  return (
                    <tr 
                      key={u.username} 
                      className={`hover:bg-slate-50/50 dark:hover:bg-slate-900/10 transition-colors group ${
                        isSelf ? 'bg-plan-cyan/5 dark:bg-plan-cyan/2' : ''
                      }`}
                    >
                      <td className="py-4 px-4 font-bold text-slate-800 dark:text-slate-200">
                        <div className="flex items-center gap-2">
                          <span>{u.username}</span>
                          {isSelf && (
                            <span className="bg-plan-cyan/15 text-plan-cyan px-1.5 py-0.5 rounded text-[8px] font-black uppercase tracking-wider border border-plan-cyan/10">
                              You
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="py-4 px-4 font-semibold">
                        <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[9px] font-black uppercase border ${
                          isAdminRole 
                            ? 'bg-indigo-500/10 text-indigo-500 border-indigo-500/15'
                            : 'bg-slate-500/10 text-slate-500 border-slate-500/15 dark:text-slate-400'
                        }`}>
                          <Shield className="w-3 h-3 shrink-0" />
                          <span>{u.role}</span>
                        </span>
                      </td>
                      <td className="py-4 px-4">
                        {isAdminRole ? (
                          <span className="text-[10px] text-slate-500 dark:text-slate-400 font-bold block">
                            All tabs (Administrative Access)
                          </span>
                        ) : u.permissions.length === 0 ? (
                          <span className="text-[10px] text-red-500/75 dark:text-red-400/60 font-semibold italic block">
                            No permissions granted
                          </span>
                        ) : (
                          <div className="flex flex-wrap gap-1">
                            {u.permissions.map(p => (
                              <span 
                                key={p} 
                                className="bg-slate-100 dark:bg-slate-800/60 px-1.5 py-0.5 rounded text-[9px] font-semibold text-slate-655 dark:text-slate-355"
                              >
                                {availableTabs.find(t => t.key === p)?.name || p}
                              </span>
                            ))}
                          </div>
                        )}
                      </td>
                      <td className="py-4 px-4 text-right">
                        <div className="flex items-center justify-end gap-1 opacity-80 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={() => handleEditClick(u)}
                            className="p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-500 dark:text-slate-400 hover:text-plan-cyan dark:hover:text-white transition-colors"
                            title="Edit User"
                          >
                            <KeyRound className="w-3.5 h-3.5" />
                          </button>
                          
                          <button
                            onClick={() => handleDeleteClick(u.username)}
                            disabled={isSelf || (users.filter(x => x.role === 'ADMIN').length <= 1 && isAdminRole)}
                            className="p-1.5 hover:bg-red-500/10 rounded-lg text-slate-400 dark:text-slate-500 hover:text-plan-red transition-colors disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-slate-400"
                            title={isSelf ? "Cannot delete yourself" : "Delete User"}
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        {/* Right Side: Create/Edit Form */}
        <div className="bg-white dark:bg-plan-card border border-slate-200 dark:border-slate-800/80 rounded-2xl p-6 shadow-sm dark:shadow-xl relative overflow-hidden transition-all">
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 rounded-xl bg-plan-blue/15 flex items-center justify-center border border-plan-blue/20">
              {editingUsername ? <UserCheck className="w-5 h-5 text-plan-blue" /> : <UserPlus className="w-5 h-5 text-plan-blue" />}
            </div>
            <div>
              <h3 className="text-sm font-black tracking-tight text-slate-800 dark:text-white">
                {editingUsername ? 'Update Account' : 'Provision User'}
              </h3>
              <span className="text-[9.5px] opacity-60 font-bold uppercase tracking-wider block mt-0.5">
                {editingUsername ? `Editing user: ${editingUsername}` : 'Create a new dashboard access account'}
              </span>
            </div>
          </div>

          <form onSubmit={handleFormSubmit} className="space-y-4">
            <div>
              <label className="block text-[9.5px] font-black uppercase tracking-widest text-slate-500 mb-1.5 pl-0.5">
                Username
              </label>
              <input
                type="text"
                required
                disabled={!!editingUsername}
                placeholder="Enter username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full px-3 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900 text-slate-800 dark:text-slate-100 text-xs focus:outline-none focus:ring-2 focus:ring-plan-cyan/40 focus:border-plan-cyan transition-all placeholder:text-slate-400 dark:placeholder:text-slate-500 disabled:opacity-60"
              />
            </div>

            <div>
              <label className="block text-[9.5px] font-black uppercase tracking-widest text-slate-500 mb-1.5 pl-0.5">
                {editingUsername ? 'New Password (Leave blank to keep current)' : 'Password'}
              </label>
              <input
                type="password"
                required={!editingUsername}
                placeholder={editingUsername ? '••••••••' : 'Enter password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-3 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900 text-slate-800 dark:text-slate-100 text-xs focus:outline-none focus:ring-2 focus:ring-plan-cyan/40 focus:border-plan-cyan transition-all placeholder:text-slate-400 dark:placeholder:text-slate-500"
              />
            </div>

            <div>
              <label className="block text-[9.5px] font-black uppercase tracking-widest text-slate-500 mb-1.5 pl-0.5">
                Portal Security Role
              </label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setRole('USER')}
                  className={`py-2 px-3 rounded-xl font-bold uppercase tracking-wider text-[9.5px] transition-all border ${
                    role === 'USER'
                      ? 'bg-plan-cyan/15 text-plan-cyan border-plan-cyan/30'
                      : 'bg-slate-50 dark:bg-slate-900/50 text-slate-500 border-slate-200 dark:border-slate-800'
                  }`}
                >
                  Regular User
                </button>
                <button
                  type="button"
                  onClick={() => setRole('ADMIN')}
                  className={`py-2 px-3 rounded-xl font-bold uppercase tracking-wider text-[9.5px] transition-all border ${
                    role === 'ADMIN'
                      ? 'bg-indigo-500/15 text-indigo-500 border-indigo-500/30'
                      : 'bg-slate-50 dark:bg-slate-900/50 text-slate-500 border-slate-200 dark:border-slate-800'
                  }`}
                >
                  Administrator
                </button>
              </div>
            </div>

            {role === 'ADMIN' ? (
              <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-slate-900/40 border border-slate-200/60 dark:border-slate-800/40 text-[10px] text-slate-500 dark:text-slate-400 flex items-start gap-2.5">
                <Info className="w-4 h-4 text-indigo-400 shrink-0 mt-0.5" />
                <div className="font-medium leading-relaxed">
                  <span className="font-extrabold text-indigo-500 dark:text-indigo-400 block uppercase tracking-wide mb-0.5">Full System Access</span>
                  Administrators automatically have access to all tabs, configurations, and administrative panels, including the user registry.
                </div>
              </div>
            ) : (
              <div className="space-y-2.5">
                <label className="block text-[9.5px] font-black uppercase tracking-widest text-slate-500 pl-0.5">
                  Authorized Tab Permissions
                </label>
                
                <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
                  {availableTabs.map((t) => {
                    const isAllowed = permissions.includes(t.key);
                    const Icon = t.icon;

                    return (
                      <button
                        type="button"
                        key={t.key}
                        onClick={() => handlePermissionToggle(t.key)}
                        className={`w-full p-2.5 rounded-xl border flex items-center justify-between text-left transition-all ${
                          isAllowed 
                            ? 'bg-plan-cyan/5 border-plan-cyan/35 text-slate-800 dark:text-white font-semibold'
                            : 'bg-slate-50/50 dark:bg-slate-900/30 border-slate-200 dark:border-slate-800 text-slate-500 hover:border-slate-300 dark:hover:border-slate-700'
                        }`}
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          <Icon className={`w-4 h-4 shrink-0 ${isAllowed ? 'text-plan-cyan' : 'text-slate-400 dark:text-slate-500'}`} />
                          <div className="min-w-0">
                            <span className="text-[10px] font-bold block">{t.name}</span>
                            <span className="text-[8.5px] opacity-60 block font-semibold truncate mt-0.5 leading-none">{t.desc}</span>
                          </div>
                        </div>
                        {isAllowed && (
                          <div className="w-4 h-4 rounded-full bg-plan-cyan/15 border border-plan-cyan/30 flex items-center justify-center shrink-0">
                            <Check className="w-2.5 h-2.5 text-plan-cyan" />
                          </div>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            <div className="flex gap-2 pt-2">
              <button
                type="submit"
                disabled={loading}
                className="flex-1 bg-gradient-to-r from-plan-cyan to-plan-blue hover:from-plan-cyan/95 hover:to-plan-blue/95 text-white font-bold text-[10px] uppercase tracking-widest py-2.5 px-4 rounded-xl shadow-md transition-all active:scale-[0.99] flex items-center justify-center gap-2"
              >
                {loading ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : null}
                <span>{editingUsername ? 'Apply Edits' : 'Create User'}</span>
              </button>

              {editingUsername && (
                <button
                  type="button"
                  onClick={handleResetForm}
                  className="px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 text-slate-500 hover:text-slate-800 dark:hover:text-white font-bold text-[10px] uppercase tracking-widest hover:bg-slate-50 dark:hover:bg-slate-900/60 transition-all"
                >
                  Cancel
                </button>
              )}
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};
