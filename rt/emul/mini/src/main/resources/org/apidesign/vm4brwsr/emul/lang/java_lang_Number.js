// empty line needed here

(function(numberPrototype) {
    function add32(x, y) {
        return (x + y) | 0;
    };
    function mul32(x, y) {
        return (((x * (y >> 16)) << 16) + x * (y & 0xFFFF)) | 0;
    };
    var __m32 = 0xFFFFFFFF;

    numberPrototype.next32 = function(low) {
        if (this === 0) {
            return low;
        }
        var l = new Number(low);
        l.hi = this | 0;
        return l;
    };

    numberPrototype.high32 = function() {
        return high32(this);
    };
    function low32(x) {
        return x.lo ? x.lo : x;
    };
    function high32(x) {
        return x.hi ? x.hi : (Math.floor(low32(x) / (__m32 + 1))) | 0;
    };
    numberPrototype.toFP = function() {
        return this.hi ? this.hi * (__m32 + 1) + low32(this) : low32(this);
    };
    numberPrototype.toLong = function() {
        var hi = (low32(this) / (__m32 + 1)) | 0;
        var low = (low32(this) % (__m32 + 1)) | 0;
        if (low < 0) {
            low += __m32 + 1;
        }

        if (low32(this) < 0) {
            hi -= 1;
        }

        return hi.next32(low);
    };

    numberPrototype.toExactString = function() {
        if (this.hi) {
            // check for Long.MIN_VALUE
            if ((this.hi == (0x80000000 | 0)) && (low32(this) == 0)) {
                return '-9223372036854775808';
            }
            var res = 0;
            var a = [6, 9, 2, 7, 6, 9, 4, 9, 2, 4];
            var s = '';
            var digit;
            var neg = this.hi < 0;
            if (neg) {
                var x = neg64(this);
                var hi = x.hi;
                var low = low32(x);
            } else {
                var hi = this.hi;
                var low = low32(this);
            }
            for (var i = 0; i < a.length; i++) {
                res += hi * a[i];
                var low_digit = low % 10;
                digit = (res % 10) + low_digit;

                low = Math.floor(low / 10);
                res = Math.floor(res / 10);

                if (digit >= 10) {
                    digit -= 10;
                    res++;
                }
                s = String(digit).concat(s);
            }
            s = String(res).concat(s).replace(/^0+/, '');
            return (neg ? '-' : '').concat(s);
        }
        return String(low32(this));
    };

    function add64(x, y) {
        var low = low32(x) + low32(y);
        carry = 0;
        if (low > __m32) {
            carry = 1;
            low -= (__m32 + 1);
        }
        var hi = (high32(x) + high32(y) + carry) | 0;
        return hi.next32(low);
    };

    function sub64(x, y) {
        var low = low32(x) - low32(y);
        carry = 0;
        if (low < 0) {
            carry = 1;
            low += (__m32 + 1);
        }
        var hi = (high32(x) - high32(y) - carry) | 0;
        return hi.next32(low);
    };

    function mul64(x, y) {
        var low = mul32(low32(x), low32(y));
        low += (low < 0) ? (__m32 + 1) : 0;
        // first count upper 32 bits of (x.low * x.low)
        var hi_hi = 0;
        var hi_low = 0;
        var m = 1;
        for (var i = 0; i < 32; i++) {
            if (low32(y) & m) {
                hi_hi += x >>> 16;
                hi_low += x & 0xFFFF
            }
            hi_low >>= 1;
            hi_low += (hi_hi & 1) ? 0x8000 : 0;
            hi_hi >>= 1;
            m <<= 1;
        }
        var hi = (hi_hi << 16) + hi_low;

        var m1 = mul32(high32(x), low32(y));
        var m2 = mul32(low32(x), high32(y));
        hi = add32(add32(hi, m1), m2);

        return hi.next32(low);
    };

    function and64(x, y) {
        var low = low32(x) & low32(y);
        low += (low < 0) ? (__m32 + 1) : 0;
        if (x.hi && y.hi) {
            var hi = x.hi & y.hi;
            return hi.next32(low);
        }
        ;
        return low;
    };

    function or64(x, y) {
        var low = low32(x) | low32(y);
        low += (low < 0) ? (__m32 + 1) : 0;
        if (x.hi || y.hi) {
            var hi = x.hi | y.hi;
            return hi.next32(low);
        }
        return low;
    };

    function xor64(x, y) {
        var low = low32(x) ^ low32(y);
        low += (low < 0) ? (__m32 + 1) : 0;
        if (x.hi || y.hi) {
            var hi = x.hi ^ y.hi;
            return hi.next32(low);
        }
        ;
        return low;
    };

    function shl64(thiz, x) {
        x &= 0x3f;
        if (x === 0) return thiz;
        if (x >= 32) {
            var hi = low32(thiz) << (x - 32);
            return hi.next32(0);
        } else {
            var hi = high32(thiz) << x;
            var low_reminder = low32(thiz) >> (32 - x);
            hi |= low_reminder;
            var low = low32(thiz) << x;
            low += (low < 0) ? (__m32 + 1) : 0;
            return hi.next32(low);
        }
    };

    function shr64(thiz, x) {
        x &= 0x3f;
        if (x === 0) return thiz;
        if (x >= 32) {
            var low = high32(thiz) >> (x - 32);
            low += (low < 0) ? (__m32 + 1) : 0;
            return low;
        } else {
            var low = low32(thiz) >>> x;
            var hi_reminder = high32(thiz) << (32 - x);
            low |= hi_reminder;
            low += (low < 0) ? (__m32 + 1) : 0;
            var hi = high32(thiz) >> x;
            return hi.next32(low);
        }
    };

    function ushr64(thiz, x) {
        x &= 0x3f;
        if (x === 0) return thiz;
        if (x >= 32) {
            var low = high32(thiz) >>> (x - 32);
            low += (low < 0) ? (__m32 + 1) : 0;
            return low;
        } else {
            var low = low32(thiz) >>> x;
            var hi_reminder = high32(thiz) << (32 - x);
            low |= hi_reminder;
            low += (low < 0) ? (__m32 + 1) : 0;
            var hi = high32(thiz) >>> x;
            return hi.next32(low);
        }
    };

    function compare64(x, y) {
        if (high32(x) === high32(y)) {
            var lox = low32(x);
            var loy = low32(y);
            return (lox < loy) ? -1 : ((lox > loy) ? 1 : 0);
        }
        return (high32(x) < high32(y)) ? -1 : 1;
    };

    function neg64(x) {
        var hi = high32(x);
        var low = low32(x);
        if ((hi === 0) && (low < 0)) {
            return -low;
        }
        hi = ~hi;
        low = ~low;
        low += (low < 0) ? (__m32 + 1) : 0;
        var ret = hi.next32(low);
        return add64(ret, 1);
    };
    
    function __handleDivByZero() {
        var exception = new vm.java_lang_ArithmeticException;
        vm.java_lang_ArithmeticException(false).constructor
          .cons__VLjava_lang_String_2.call(exception, "/ by zero");

        throw exception;
    }

    function __Int64(hi32, lo32) {
        this.hi32 = hi32 | 0;
        this.lo32 = lo32 | 0;

        this.get32 = function(bitIndex) {
            var v0;
            var v1;
            bitIndex += 32;
            var selector = bitIndex >>> 5;
            switch (selector) {
                case 0:
                    v0 = 0;
                    v1 = this.lo32;
                    break;
                case 1:
                    v0 = this.lo32;
                    v1 = this.hi32;
                    break;
                case 2:
                    v0 = this.hi32;
                    v1 = 0;
                    break
                default:
                    return 0;
            }

            var shift = bitIndex & 31;
            if (shift === 0) {
                return v0;
            }

            return (v1 << (32 - shift)) | (v0 >>> shift);
        }

        this.get16 = function(bitIndex) {
            return this.get32(bitIndex) & 0xffff;
        }

        this.set16 = function(bitIndex, value) {
            bitIndex += 32;
            var shift = bitIndex & 15;
            var svalue = (value & 0xffff) << shift; 
            var smask = 0xffff << shift;
            var selector = bitIndex >>> 4;
            switch (selector) {
                case 0:
                    break;
                case 1:
                    this.lo32 = (this.lo32 & ~(smask >>> 16))
                                    | (svalue >>> 16);
                    break;
                case 2:
                    this.lo32 = (this.lo32 & ~smask) | svalue;
                    break;
                case 3:
                    this.lo32 = (this.lo32 & ~(smask << 16))
                                    | (svalue << 16);
                    this.hi32 = (this.hi32 & ~(smask >>> 16))
                                    | (svalue >>> 16);
                    break;
                case 4:
                    this.hi32 = (this.hi32 & ~smask) | svalue;
                    break;
                case 5:
                    this.hi32 = (this.hi32 & ~(smask << 16))
                                    | (svalue << 16);
                    break;
            }
        }

        this.getDigit = function(index, shift) {
            return this.get16((index << 4) - shift);
        }

        this.getTwoDigits = function(index, shift) {
            return this.get32(((index - 1) << 4) - shift);
        }

        this.setDigit = function(index, shift, value) {
            this.set16((index << 4) - shift, value);
        }

        this.countSignificantDigits = function() {
            var sd;
            var remaining;

            if (this.hi32 === 0) {
                if (this.lo32 === 0) {
                    return 0;
                }

                sd = 2;
                remaining = this.lo32;
            } else {
                sd = 4;
                remaining = this.hi32;
            }

            if (remaining < 0) {
                return sd;
            }

            return (remaining < 65536) ? sd - 1 : sd;
        }
        
        this.toNumber = function() {
            var lo32 = this.lo32;
            if (lo32 < 0) {
                lo32 += 0x100000000;
            }

            return this.hi32.next32(lo32);
        }
    }

    function __countLeadingZeroes16(number) {
        var nlz = 0;

        if (number < 256) {
            nlz += 8;
            number <<= 8;
        }

        if (number < 4096) {
            nlz += 4;
            number <<= 4;
        }

        if (number < 16384) {
            nlz += 2;
            number <<= 2;
        }

        return (number < 32768) ? nlz + 1 : nlz;
    }
    
    // q = u / v; r = u - q * v;
    // v != 0
    function __div64(q, r, u, v) {
        var m = u.countSignificantDigits();
        var n = v.countSignificantDigits();

        q.hi32 = q.lo32 = 0;

        if (n === 1) {
            // v has single digit
            var vd = v.getDigit(0, 0);
            var carry = 0;
            for (var i = m - 1; i >= 0; --i) {
                var ui = (carry << 16) | u.getDigit(i, 0);
                if (ui < 0) {
                    ui += 0x100000000;
                }
                var qi = (ui / vd) | 0;
                q.setDigit(i, 0, qi);
                carry = ui - qi * vd;
            }

            r.hi32 = 0;
            r.lo32 = carry;
            return;
        }

        r.hi32 = u.hi32;  
        r.lo32 = u.lo32;

        if (m < n) {
            return;
        }

        // Normalize
        var nrm = __countLeadingZeroes16(v.getDigit(n - 1, 0));

        var vd1 = v.getDigit(n - 1, nrm);                
        var vd0 = v.getDigit(n - 2, nrm);
        for (var j = m - n; j >= 0; --j) {
            // Calculate qj estimate
            var ud21 = r.getTwoDigits(j + n, nrm);
            var ud2 = ud21 >>> 16;
            if (ud21 < 0) {
                ud21 += 0x100000000;
            }

            var qest = (ud2 === vd1) ? 0xFFFF : ((ud21 / vd1) | 0);
            var rest = ud21 - qest * vd1;

            // 0 <= (qest - qj) <= 2

            // Refine qj estimate
            var ud0 = r.getDigit(j + n - 2, nrm);
            while ((qest * vd0) > ((rest * 0x10000) + ud0)) {
                --qest;
                rest += vd1;
            }

            // 0 <= (qest - qj) <= 1
            
            // Multiply and subtract
            var carry = 0;
            for (var i = 0; i < n; ++i) {
                var vi = qest * v.getDigit(i, nrm);
                var ui = r.getDigit(i + j, nrm) - carry - (vi & 0xffff);
                r.setDigit(i + j, nrm, ui);
                carry = (vi >>> 16) - (ui >> 16);
            }
            var uj = ud2 - carry;

            if (uj < 0) {
                // qest - qj = 1

                // Add back
                --qest;
                var carry = 0;
                for (var i = 0; i < n; ++i) {
                    var ui = r.getDigit(i + j, nrm) + v.getDigit(i, nrm)
                                 + carry;
                    r.setDigit(i + j, nrm, ui);
                    carry = ui >> 16;
                }
                uj += carry;
            }

            q.setDigit(j, 0, qest);
            r.setDigit(j + n, nrm, uj);
        }
    }

    function div64(x, y) {
        var negateResult = false;
        var u, v;

        if ((high32(x) & 0x80000000) !== 0) {
            u = neg64(x);
            negateResult = !negateResult;
        } else {
            u = x;
        }

        if ((high32(y) & 0x80000000) !== 0) {
            v = neg64(y);
            negateResult = !negateResult;
        } else {
            v = y;
        }

        if ((low32(v) === 0) && (high32(v) === 0)) {
            __handleDivByZero();
        }

        if (high32(u) === 0) {
            if (high32(v) === 0) {
                var result = (low32(u) / low32(v)) | 0;
                return negateResult ? neg64(result) : result;
            }

            return 0;
        }

        var u64 = new __Int64(high32(u), low32(u));
        var v64 = new __Int64(high32(v), low32(v));
        var q64 = new __Int64(0, 0);
        var r64 = new __Int64(0, 0);

        __div64(q64, r64, u64, v64);

        var result = q64.toNumber();
        return negateResult ? neg64(result) : result;
    }

    function mod64(x, y) {
        var negateResult = false;
        var u, v;
        
        if ((high32(x) & 0x80000000) !== 0) {
            u = neg64(x);
            negateResult = !negateResult;
        } else {
            u = x;
        }

        if ((high32(y) & 0x80000000) !== 0) {
            v = neg64(y);
        } else {
            v = y;
        }

        if ((low32(v) === 0) && (high32(v) === 0)) {
            __handleDivByZero();
        }

        if (high32(u) === 0) {
            var result = (high32(v) === 0) ? (low32(u) % low32(v)) : low32(u);
            return negateResult ? neg64(result) : result;
        }

        var u64 = new __Int64(high32(u), low32(u));
        var v64 = new __Int64(high32(v), low32(v));
        var q64 = new __Int64(0, 0);
        var r64 = new __Int64(0, 0);

        __div64(q64, r64, u64, v64);

        var result = r64.toNumber();
        return negateResult ? neg64(result) : result;
    }

    var b = numberPrototype['__bit64'] = {};
    b.add64 = add64;
    b.sub64 = sub64;
    b.mul64 = mul64;
    b.div64 = div64;
    b.mod64 = mod64;
    b.and64 = and64;
    b.or64 = or64;
    b.xor64 = xor64;
    b.neg64 = neg64;
    b.shl64 = shl64;
    b.shr64 = shr64;
    b.ushr64 = ushr64;
    b.compare64 = compare64;
})(Number.prototype);

vm.java_lang_Number(false);
