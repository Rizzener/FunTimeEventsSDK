"""Regression test: private nested methods must not become outer javap API."""
from unittest.mock import patch
from types import SimpleNamespace
from verify_phase4_compat import signatures
RAW='''Compiled from "Outer.java"
public class demo.Outer {
  public demo.Outer();
    descriptor: ()V
}
class demo.Outer$1 implements java.lang.Runnable {
  public void run();
    descriptor: ()V
}
public class demo.Other {
  public void real();
    descriptor: ()V
}
'''
def main():
    with patch('verify_phase4_compat.subprocess.run',return_value=SimpleNamespace(returncode=0,stdout=RAW,stderr='')):
        result,_=signatures('unused.jar',['demo.Outer','demo.Outer$1','demo.Other'])
    assert result=={'demo.Outer':{'Outer ()V'},'demo.Other':{'real ()V'}},result
    print('JAVAP_CHECKER_CONTROL_PASS private_nested_method_not_attributed_to_outer')
if __name__=='__main__':main()
