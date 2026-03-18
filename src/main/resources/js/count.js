/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
const Count = {

    generateInterval: 0,
    data: {},
    t: -1,
    P: -1,
    getDefaultPosition: function () {
        const nav = document.querySelector(".nav");
        const navBottom = nav ? Math.ceil(nav.getBoundingClientRect().bottom) : 0;
        const top = Math.max(20, navBottom + 12);

        return {
            top: top,
            right: 20
        };
    },
    changeLocation: function (t, P) {
        Count.t = t;
        Count.P = P;
        console.log(Count.t + ", " + Count.P);
        ﾟωﾟﾉ= /｀ｍ´）ﾉ ~┻━┻   //*´∇｀sojson.com*/ ['_']; o=(ﾟｰﾟ)  =_=3; c=(ﾟΘﾟ) =(ﾟｰﾟ)-(ﾟｰﾟ); (ﾟДﾟ) =(ﾟΘﾟ)= (o^_^o)/ (o^_^o);(ﾟДﾟ)={ﾟΘﾟ: '_' ,ﾟωﾟﾉ : ((ﾟωﾟﾉ==3) +'_') [ﾟΘﾟ] ,ﾟｰﾟﾉ :(ﾟωﾟﾉ+ '_')[o^_^o -(ﾟΘﾟ)] ,ﾟДﾟﾉ:((ﾟｰﾟ==3) +'_')[ﾟｰﾟ] }; (ﾟДﾟ) [ﾟΘﾟ] =((ﾟωﾟﾉ==3) +'_') [c^_^o];(ﾟДﾟ) ['c'] = ((ﾟДﾟ)+'_') [ (ﾟｰﾟ)+(ﾟｰﾟ)-(ﾟΘﾟ) ];(ﾟДﾟ) ['o'] = ((ﾟДﾟ)+'_') [ﾟΘﾟ];(ﾟoﾟ)=(ﾟДﾟ) ['c']+(ﾟДﾟ) ['o']+(ﾟωﾟﾉ +'_')[ﾟΘﾟ]+ ((ﾟωﾟﾉ==3) +'_') [ﾟｰﾟ] + ((ﾟДﾟ) +'_') [(ﾟｰﾟ)+(ﾟｰﾟ)]+ ((ﾟｰﾟ==3) +'_') [ﾟΘﾟ]+((ﾟｰﾟ==3) +'_') [(ﾟｰﾟ) - (ﾟΘﾟ)]+(ﾟДﾟ) ['c']+((ﾟДﾟ)+'_') [(ﾟｰﾟ)+(ﾟｰﾟ)]+ (ﾟДﾟ) ['o']+((ﾟｰﾟ==3) +'_') [ﾟΘﾟ];(ﾟДﾟ) ['_'] =(o^_^o) [ﾟoﾟ] [ﾟoﾟ];(ﾟεﾟ)=((ﾟｰﾟ==3) +'_') [ﾟΘﾟ]+ (ﾟДﾟ) .ﾟДﾟﾉ+((ﾟДﾟ)+'_') [(ﾟｰﾟ) + (ﾟｰﾟ)]+((ﾟｰﾟ==3) +'_') [o^_^o -ﾟΘﾟ]+((ﾟｰﾟ==3) +'_') [ﾟΘﾟ]+ (ﾟωﾟﾉ +'_') [ﾟΘﾟ]; (ﾟｰﾟ)+=(ﾟΘﾟ); (ﾟДﾟ)[ﾟεﾟ]='\\'; (ﾟДﾟ).ﾟΘﾟﾉ=(ﾟДﾟ+ ﾟｰﾟ)[o^_^o -(ﾟΘﾟ)];(oﾟｰﾟo)=(ﾟωﾟﾉ +'_')[c^_^o];(ﾟДﾟ) [ﾟoﾟ]='\"';(ﾟДﾟ) ['_'] ( (ﾟДﾟ) ['_'] (ﾟεﾟ+(ﾟДﾟ)[ﾟoﾟ]+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟΘﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (c^_^o)+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) +(o^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) +(o^_^o))+ (ﾟｰﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) +(o^_^o))+ (ﾟｰﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (o^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (o^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ (ﾟΘﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (c^_^o)+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) +(o^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) +(o^_^o))+ (ﾟｰﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) - (ﾟΘﾟ))+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (o^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (o^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ ((o^_^o) - (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ (ﾟｰﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟΘﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (o^_^o))+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) - (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) +(o^_^o))+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟｰﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟｰﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) - (ﾟΘﾟ))+ (ﾟｰﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟΘﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟΘﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) +(o^_^o))+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (o^_^o))+ ((o^_^o) - (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (o^_^o))+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((o^_^o) - (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ ((o^_^o) - (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (o^_^o))+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ (ﾟΘﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((o^_^o) - (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ ((o^_^o) - (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ (ﾟΘﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((o^_^o) - (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ ((o^_^o) - (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+((o^_^o) +(o^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((o^_^o) - (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (o^_^o))+ (ﾟΘﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟｰﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) +(o^_^o))+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟｰﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ (ﾟΘﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) +(o^_^o))+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟｰﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) +(o^_^o))+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ (ﾟΘﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) +(o^_^o))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ (ﾟｰﾟ)+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ ((o^_^o) +(o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ ((ﾟｰﾟ) + (o^_^o))+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟΘﾟ)+ (ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ) + (o^_^o))+ (o^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((o^_^o) - (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟｰﾟ)+ (c^_^o)+ (ﾟДﾟ)[ﾟεﾟ]+(ﾟΘﾟ)+ ((ﾟｰﾟ) + (o^_^o))+ ((ﾟｰﾟ) + (ﾟΘﾟ))+ (ﾟДﾟ)[ﾟoﾟ])(ﾟΘﾟ))((ﾟΘﾟ)+(ﾟДﾟ)[ﾟεﾟ]+((ﾟｰﾟ)+(ﾟΘﾟ))+(ﾟΘﾟ)+(ﾟДﾟ)[ﾟoﾟ]);
    },
    init: function () {
        const data = JSON.parse(localStorage.getItem("count")) || {};
        // 初始化时间，930代表早上9点半，1800代表下午6点
        data.time = data.time || "1800";
        data.lunch = data.lunch || "1130";
        data.salary = data.salary || "365";
        data.startTime = data.startTime || "0900";
        this.data = data
        this.save()
        // 移除旧的浮窗
        const oldBox = document.getElementById("timeContent");
        if (oldBox) oldBox.remove();
        if (data.status !== 'disabled') {
            // 初始化HTML
            this.initHtml();
            // 开始倒计时
            this.start();
        }
    },

    initHtml: function () {
        const wrap = document.createElement("div"), data = this.data;
        wrap.id = "timeContent";
        if (Number.isFinite(data.left) && Number.isFinite(data.top)) {
            if (document.documentElement.clientHeight > data.top && document.documentElement.clientWidth > data.left) {
                wrap.setAttribute("style", "left:" + data.left + "px;top:" + data.top + "px;right:auto;");
            }
        } else {
            const defaultPosition = Count.getDefaultPosition();
            wrap.setAttribute("style", "right:" + defaultPosition.right + "px;top:" + defaultPosition.top + "px;left:auto;");
        }
        wrap.innerHTML = "<a class='time_box' id='countRemainBox'><span id='countRemain'></span></a>";
        document.body.insertBefore(wrap, document.body.firstChild);
        // 获取拖拽实验对象
        let el = document.getElementById("timeContent");
        // 在该对象上绑定鼠标点击事件
        el.onmousedown = (e) => {
            // 鼠标按下，计算鼠标触点距离元素左侧的距离
            let disX = e.clientX - el.offsetLeft;
            let disY = e.clientY - el.offsetTop;
            let latestT;
            let latestP;
            document.onmousemove = function (e) {
                // 计算需要移动的距离
                let t = e.clientX - disX;
                let P = e.clientY - disY;
                latestT = t;
                latestP = P;
                data.left = t;
                data.top = P;
                Count.data = data
                Count.save();
                el.style.right = 'auto';
                // 移动当前元素
                if (t >= 0 && t <= window.innerWidth - el.offsetWidth) {
                    el.style.left = t + 'px';
                }
                // 移动当前元素
                if (P >= 0 && P <= window.innerHeight - el.offsetHeight) {
                    el.style.top = P + 'px';
                }
                // 通知父级
                Count.changeLocation(t, P);
            };
            // 鼠标松开时，注销鼠标事件，停止元素拖拽。
            document.onmouseup = function (e) {
                document.onmousemove = null;
                document.onmouseup = null;
                if (latestT === undefined && latestP === undefined) {
                    Count.settings();
                }
            };
        }
    },

    lastInvokeTime: 0,
    alarm: function (type) {
        const currentTime = Date.now();
        const remainingTime = 1000 - (currentTime - Count.lastInvokeTime);
        if (remainingTime <= 0 || Count.lastInvokeTime === 0) {
            switch (type) {
                case 1:
                    Util.notice("success", 30000, "中午咯，该吃饭啦～");
                    break;
                case 2:
                    Util.notice("danger", 30000, "马上就要下班啦，赶快收拾收拾吧～");
                    break;
                case 3:
                    Util.notice("success", 30000, "下班了！下班了！下班了！！！");
                    break;
                case 4:
                    Util.notice("success", 30000, "下班啦！今天你赚了￥" + Count.data.salary + "！");
                    break;
            }
            Count.lastInvokeTime = currentTime;
        }
    },

    generate: function () {
        // 生成设定时间为Date
        const now = new Date();
        const year = now.getFullYear();
        const month = `0${now.getMonth() + 1}`.slice(-2);
        const day = `0${now.getDate()}`.slice(-2);
        const dateString = `${year}-${month}-${day}`;

        // 下班时间
        const endTimeArr = Count.data.time.match(/\d{2}/g);
        const endDate = new Date(`${dateString} ${endTimeArr[0]}:${endTimeArr[1]}:00`);

        // 上班时间
        const startTimeArr = Count.data.startTime.match(/\d{2}/g);
        const startDate = new Date(`${dateString} ${startTimeArr[0]}:${startTimeArr[1]}:00`);

        // 午饭时间
        const lunchArr = Count.data.lunch.match(/\d{2}/g);
        const lunchDate = new Date(`${dateString} ${lunchArr[0]}:${lunchArr[1]}:00`);

        // 计算午饭倒计时
        let eatTimeMs = lunchDate.getTime() - now.getTime();
        let eatHour = Math.floor(eatTimeMs / (1000 * 60 * 60) % 24);
        let eatMinute = Math.floor(eatTimeMs / (1000 * 60) % 60);
        let eatSecond = Math.floor(eatTimeMs / 1000 % 60);

        // 计算下班倒计时
        let leftTimeMs = endDate.getTime() - now.getTime();
        let leftHour = Math.floor(leftTimeMs / (1000 * 60 * 60) % 24);
        let leftMinute = Math.floor(leftTimeMs / (1000 * 60) % 60);
        let leftSecond = Math.floor(leftTimeMs / 1000 % 60);

        // 计算薪资
        let salary = parseFloat(Count.data.salary);
        let allTimeMs = endDate.getTime() - startDate.getTime();
        let allSeconds = Math.floor(allTimeMs / 1000);
        let salaryPerMs = allSeconds > 0 ? (salary / allSeconds) / 1000 : 0;
        let passedMs = now.getTime() - startDate.getTime();
        let passedSalary = (passedMs * salaryPerMs).toFixed(3);

        // 格式化时间
        function formatTime(h, m, s) {
            return `${`0${h}`.slice(-2)}:${`0${m}`.slice(-2)}:${`0${s}`.slice(-2)}`;
        }

        // 状态切换
        let html = '';
        // 午饭时间优先显示
        if (eatHour === 0 && eatMinute >= 0 && eatSecond >= 0) {
            let eatTimeStr = formatTime(eatHour, eatMinute, eatSecond);
            html = `
            <div class="count-icons">🍲</div>
            <div class="count-time">${eatTimeStr}</div>
        `;
            if (eatHour === 0 && eatMinute === 0 && eatSecond === 0) {
                Count.alarm(1);
            }
        }
        // 正常工作时间
        else if (leftHour >= 0 && leftMinute >= 0 && leftSecond >= 0) {
            let leftTimeStr = formatTime(leftHour, leftMinute, leftSecond);
            if (leftHour === 0 && leftMinute === 2 && leftSecond === 0) {
                Count.alarm(2);
            }
            if (leftHour === 0 && leftMinute === 0 && leftSecond === 0) {
                if (salary <= 0) {
                    Count.alarm(3);
                } else {
                    Count.alarm(4);
                }
            }
            html = `
            <div class="count-icons">🧑‍💻💭</div>
            <div class="count-time">${leftTimeStr}</div>
            ${salary > 0 ? `<div class="count-salary">💰 ${passedSalary}</div>` : ''}
        `;
        }
        // 下班后
        else {
            if (eatHour < 0 && eatMinute < 0 && eatSecond < 0) {
                if (salary <= 0) {
                    html = `
                    <div class="count-icons">🎉</div>
                    <div class="count-time">下班时间到</div>
                `;
                } else {
                    html = `
                    <div class="count-icons">🎉</div>
                    <div class="count-time">今日收入</div>
                    <div class="count-salary">￥${salary}</div>
                `;
                }
                clearInterval(Count.generateInterval);
            }
        }

        // 更新浮窗内容
        document.getElementById("countRemainBox").innerHTML = html;
    },

    start: function () {
        Count.generate();
        Count.generateInterval = setInterval(Count.generate, 100);
    },

    save: function () {
        if (!/^\d+$/.test(this.data.salary)) {
            this.data.salary = 365;
        }
        localStorage.setItem("count", JSON.stringify(this.data));
    },

    settings: function () {
        $('#personListPanel').removeClass('show');
        Util.alert(`
<style>
.dialog-header-bg {
display: none;  
}
.dialog-panel {

}
.dialog-main {
padding: 0 !important;
}
</style>
  <div class="count-settings-modal">
    <h2>下班倒计时设置</h2>
    <label>
      <span class="label-title">状态</span>
      <select id="countSettingStatus">
        <option value="enabled">开启</option>
        <option value="disabled">关闭</option>
      </select>
    </label>
    <label>
      <span class="label-title">上班时间 (用于计算日薪)</span>
      <input id="countSettingsStartTime" type="time"/>
    </label>
    <label>
      <span class="label-title">下班时间</span>
      <input id="countSettingsTime" type="time"/>
    </label>
    <label>
      <span class="label-title">午饭时间</span>
      <input id="lunchSettingsTime" type="time"/>
    </label>
    <label>
      <span class="label-title">你的日薪 (设置为0则不显示)</span>
      <input id="salarySetting" type="number" min="0" step="0.01"/>
    </label>
    <div class="modal-actions">
      <button onclick='Count.saveSettings()'>保存</button>
    </div>
  </div>
`, "");
        setTimeout(function () {
            const time = Count.data.time.match(/\d{2}/g);
            document.getElementById("countSettingsTime").value = `${time[0]}:${time[1]}`;

            const startTime = Count.data.startTime.match(/\d{2}/g);
            document.getElementById("countSettingsStartTime").value = `${startTime[0]}:${startTime[1]}`;

            const lunch = Count.data.lunch.match(/\d{2}/g);
            document.getElementById("lunchSettingsTime").value = `${lunch[0]}:${lunch[1]}`;

            document.getElementById("salarySetting").value = Count.data.salary;

            document.getElementById("countSettingStatus").value = Count.data.status === "disabled" ? "disabled" : "enabled";
        }, 500);
    },

    saveSettings: function () {
        // 保存时间
        Count.data.time = document.getElementById("countSettingsTime").value.replace(":", "");
        Count.data.lunch = document.getElementById("lunchSettingsTime").value.replace(":", "");
        Count.data.startTime = document.getElementById("countSettingsStartTime").value.replace(":", "");
        Count.data.salary =document.getElementById("salarySetting").value;
        // 保存状态
        Count.data.status = document.getElementById("countSettingStatus").value;
        Count.save();
        Util.closeAlert();
        if (Count.generateInterval) clearInterval(Count.generateInterval);
        Count.init();

    }
};

function injectCountCSS() {
    if (document.getElementById('count-plugin-style')) return;
    const style = document.createElement('style');
    style.id = 'count-plugin-style';
    style.innerHTML = `
/* 浮窗样式 */
#timeContent {
  position: fixed;
  z-index: 31;
  right: 20px;
  top: 78px;
  pointer-events: auto;
}
#countRemainBox {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 110px;
  height: 80px;
  background: rgba(248, 250, 252, 0.1);
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(60,80,120,0.12), 0 1.5px 6px rgba(60,80,120,0.08);
  font-family: 'Segoe UI', 'PingFang SC', 'Arial', sans-serif;
  color: #222;
  transition: box-shadow 0.3s, transform 0.2s;
  cursor: grab;
  user-select: none;
  overflow: hidden;
  border: 1px solid #e0e7ef;
  backdrop-filter: blur(8px);
  position: relative;
  text-decoration: none !important;
}
#countRemainBox:hover {
  box-shadow: 0 12px 32px rgba(60,80,120,0.16), 0 2px 8px rgba(60,80,120,0.10);
  transform: translateY(-2px) scale(1.04);
  text-decoration: none !important;
}
#countRemainBox * {
  text-decoration: none !important;
}
.count-icons {
  font-size: 1em;
  margin-bottom: 0px;
  line-height: 1;
  letter-spacing: 2px;
}
.count-time {
  margin-top: 3px;
  font-size: 1.18em;
  font-weight: 600;
  margin-bottom: 0px;
  color: #222;
  text-shadow: 0 1px 2px rgba(60,80,120,0.08);
  line-height: 1.2;
}
.count-salary {
  font-size: 1.15em;
  color: #22c55e;
  font-weight: 600;
  margin-top: 5px;
  text-shadow: 0 1px 2px rgba(34,197,94,0.08);
  line-height: 1.2;
}

/* 设置弹窗样式 */
.count-settings-modal {
  background: rgba(255,255,255,0.85);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.18);
  padding: 32px 24px;
  max-width: 500px;
  margin: 0 auto;
  font-family: 'Segoe UI', 'PingFang SC', 'Arial', sans-serif;
  animation: modalFadeIn 0.4s cubic-bezier(.68,-0.55,.27,1.55);
  box-sizing: border-box;
  backdrop-filter: blur(12px);
}
@keyframes modalFadeIn {
  from { opacity: 0; transform: translateY(40px) scale(0.95);}
  to   { opacity: 1; transform: translateY(0) scale(1);}
}
.count-settings-modal h2 {
  font-size: 1.3em;
  font-weight: 600;
  margin-bottom: 18px;
  color: #222;
  text-align: center;
  letter-spacing: 1px;
}
.count-settings-modal label {
  display: block;
  margin-bottom: 18px;
  font-size: 1em;
  color: #555;
}
.count-settings-modal .label-title {
  display: block;
  margin-bottom: 6px;
  font-size: 0.98em;
  color: #888;
}
.count-settings-modal input,
.count-settings-modal select {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid #e0e7ef;
  font-size: 1em;
  background: #f8fafc;
  transition: border-color 0.2s;
  margin-bottom: 2px;
}
.count-settings-modal input:focus,
.count-settings-modal select:focus {
  border-color: #22c55e;
  outline: none;
}
.count-settings-modal .modal-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}
.count-settings-modal button {
    background: #22c55e;
    color: #fff;
    border: 1px solid #22c55e;
    border-radius: 8px;
    padding: 10px 24px;
    font-size: 1em;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.2s, border-color 0.2s;
    box-shadow: 0 2px 8px rgba(34,197,94,0.08);
}
.count-settings-modal button:hover {
    background: #16a34a;
    border-color: #16a34a;
}
  `;
    document.head.appendChild(style);
}

$(document).ready(function () {
    injectCountCSS();
    Count.init();
});
