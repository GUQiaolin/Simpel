# Independent reference implementation (written from the equations, not translated from the Java code)
import math
sig=5.67e-8; k=0.41; cp=1005.; Rd=287.04
def es(T): return 6.108*math.exp(17.27*T/(T+237.3))
def q(e,p): return 0.622*e/(p-0.378*e)
def lam(T): return (2.501-0.002361*T)*1e6
def case(K,L,T,RH,p,u,lai,laimax,smd,smdwp,dRn):
    Rn=(1-0.18)*K+0.98*(L-sig*(T+273.15)**4)
    Qs=0.32*Rn+0.54*dRn-27.4
    A=Rn-Qs
    h=0.12; d=0.67*h; z0=0.123*h; zv=0.1*z0; z=2.0
    ra=math.log((z-d)/z0)*math.log((z-d)/zv)/(k*k*max(u,0.5))
    gl=min(1,lai/laimax)
    kk=min(max(K,0),1200); gk=(kk/(566.0923+kk))/(1200/(566.0923+1200))
    dq=1000*(q(es(T),p)-q(es(T)*RH/100,p)); gq=1-0.2163*min(dq,3.3649)
    TL,TH,G5=0.,40.,11.0764; tc=(TH-G5)/(G5-TL)
    gt=(T-TL)*(TH-T)**tc/((G5-TL)*(TH-G5)**tc) if TL<T<TH else 0
    gs_=(1-math.exp(0.0176*(smd-smdwp)))/(1-math.exp(-0.0176*smdwp)); gs_=min(1,max(0,gs_))
    gs=max(16.4764*gl*gk*max(gq,0)*gt*gs_,0.01); rs=1000/gs
    e=es(T); vpd=e*(1-RH/100); s=4098*e/(T+237.3)**2; rho=p*100/(Rd*(T+273.15)); gam=cp*p/(0.622*lam(T))
    pm=lambda r:(s*A+rho*cp*vpd/ra)/(s+gam*(1+r/ra))
    return dict(Rn=Rn,Qs=Qs,ra=ra,gs=gs,QEwet=pm(0),QEdry=pm(rs),ETdry_mm_h=max(0,pm(rs))*3600/lam(T))
print('midday',case(800,350,25,50,1010,3,2,2,20,130,50))
print('night ',case(0,300,12,85,1010,1.5,2,2,20,130,0))
print('dry   ',case(800,350,25,50,1010,3,2,2,125,130,50))
